#!/usr/bin/python
import asyncio
import datetime
import websockets
import json
import logging
from collections import defaultdict
from typing import Set, DefaultDict, List, Dict, Any

logging.basicConfig(
    filename='/app/logs/websocket.log',
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

class TrackingServer:
    def __init__(self):
        self.connected_clients: Set[websockets.WebSocketServerProtocol] = set()
        self.tracking_history: DefaultDict[str, List[Dict[str, Any]]] = defaultdict(list)
        self.active_sessions: Set[str] = set()  # Track active recording sessions
        self.last_activity: Dict[str, datetime.datetime] = {}  # Track when each session was last updated
        self.activity_timeout = 60  # Consider a session inactive after this many seconds without updates
        self.timestamp_format = '%d-%m-%Y %H:%M:%S'
        self.batch_size = 100

    async def broadcast_update(self, message: Dict[str, Any]) -> None:
        """Broadcast message to all connected clients."""
        if not self.connected_clients:
            return

        disconnected_clients = set()

        for client in self.connected_clients.copy():
            try:
                await client.send(json.dumps(message))
            except websockets.exceptions.ConnectionClosed:
                disconnected_clients.add(client)
            except Exception as e:
                logging.error(f"Error broadcasting to client: {str(e)}")
                disconnected_clients.add(client)

        # Remove disconnected clients
        self.connected_clients.difference_update(disconnected_clients)
        if disconnected_clients:
            logging.info(f"Removed {len(disconnected_clients)} disconnected clients")

    async def send_history(self, websocket: websockets.WebSocketServerProtocol) -> None:
        """Send historical data to newly connected client in batches."""
        try:
            # Check for stale active sessions before sending data
            self.update_active_sessions()

            # Gather and sort all points from all sessions
            all_points = []
            for session_id, points in self.tracking_history.items():
                all_points.extend(points)

            all_points.sort(key=lambda x: datetime.datetime.strptime(x['timestamp'], self.timestamp_format))

            # Send points in batches
            for i in range(0, len(all_points), self.batch_size):
                batch = all_points[i:i + self.batch_size]
                await websocket.send(json.dumps({
                    'type': 'history_batch',
                    'points': batch
                }))
                await asyncio.sleep(0.001)  # Minimal delay between batches

            # Send session IDs for UI along with active status
            session_info = [
                {
                    "sessionId": session_id,
                    "isActive": session_id in self.active_sessions
                }
                for session_id in self.tracking_history.keys()
            ]

            await websocket.send(json.dumps({
                'type': 'session_list',
                'sessions': session_info
            }))

            # Send completion message
            await websocket.send(json.dumps({
                'type': 'history_complete'
            }))

            logging.info(f"Sent {len(all_points)} historical points to client")

        except Exception as e:
            logging.error(f"Error sending history: {str(e)}")
            raise  # Re-raise to be handled by the caller

    def validate_tracking_point(self, message_data: Dict[str, Any]) -> bool:
        """Validate required fields in tracking point data."""
        required_fields = [
            "person",
            "sessionId",
            "latitude",
            "longitude",
            "distance",
            "currentSpeed",
            "maxSpeed",
            "movingAverageSpeed",
            "averageSpeed"
        ]

        return all(
            key in message_data and message_data[key] is not None
            for key in required_fields
        )

    def create_tracking_point(self, message_data: Dict[str, Any]) -> Dict[str, Any]:
        """Create a tracking point with current timestamp."""
        session_id = message_data.get('sessionId')
        if session_id:
            # Mark this session as active
            self.active_sessions.add(session_id)
            self.last_activity[session_id] = datetime.datetime.now()

        return {
            "timestamp": datetime.datetime.now().strftime(self.timestamp_format),
            **message_data,
            "currentSpeed": float(message_data["currentSpeed"]),
            "maxSpeed": float(message_data["maxSpeed"]),
            "movingAverageSpeed": float(message_data["movingAverageSpeed"]),
            "averageSpeed": float(message_data["averageSpeed"])
        }

    def update_active_sessions(self) -> None:
        """Update the list of active sessions based on recent activity."""
        now = datetime.datetime.now()
        inactive_sessions = set()

        for session_id, last_time in self.last_activity.items():
            time_diff = (now - last_time).total_seconds()
            if time_diff > self.activity_timeout:
                inactive_sessions.add(session_id)

        # Remove inactive sessions
        for session_id in inactive_sessions:
            if session_id in self.active_sessions:
                self.active_sessions.remove(session_id)
                logging.info(f"Session {session_id} marked as inactive after {self.activity_timeout} seconds without updates")

    async def delete_session(self, session_id: str) -> Dict[str, Any]:
        """Delete a session by its ID if it's not active."""
        try:
            # Check if session exists
            if session_id not in self.tracking_history:
                logging.warning(f"Attempted to delete non-existent session: {session_id}")
                return {"success": False, "reason": "Session does not exist"}

            # Check if session is active
            self.update_active_sessions()  # Refresh active sessions status first
            if session_id in self.active_sessions:
                logging.warning(f"Attempted to delete active session: {session_id}")
                return {"success": False, "reason": "Cannot delete active session"}

            # Delete the session
            del self.tracking_history[session_id]
            if session_id in self.last_activity:
                del self.last_activity[session_id]

            logging.info(f"Deleted session: {session_id}")

            # Notify all clients about the deletion
            await self.broadcast_update({
                'type': 'session_deleted',
                'sessionId': session_id
            })

            return {"success": True}

        except Exception as e:
            logging.error(f"Error deleting session {session_id}: {str(e)}")
            return {"success": False, "reason": str(e)}

    async def handle_client(self, websocket: websockets.WebSocketServerProtocol) -> None:
        """Handle individual WebSocket client connection."""
        client_address = websocket.remote_address

        try:
            self.connected_clients.add(websocket)
            logging.info(f"New client connected from {client_address}")

            # Send historical data
            await self.send_history(websocket)

            # Handle incoming messages
            async for message in websocket:
                try:
                    if message == "ping":
                        await websocket.send("pong")
                        continue

                    logging.info(f"Received message: {message}")

                    message_data = json.loads(message)

                    # Handle delete request
                    if message_data.get('type') == 'delete_session':
                        session_id = message_data.get('sessionId')
                        if session_id:
                            result = await self.delete_session(session_id)
                            await websocket.send(json.dumps({
                                'type': 'delete_response',
                                'sessionId': session_id,
                                'success': result["success"],
                                'reason': result.get("reason", "")
                            }))
                        continue

                    # Handle session status request
                    if message_data.get('type') == 'request_sessions':
                        self.update_active_sessions()  # Refresh active status
                        session_info = [
                            {
                                "sessionId": session_id,
                                "isActive": session_id in self.active_sessions
                            }
                            for session_id in self.tracking_history.keys()
                        ]

                        await websocket.send(json.dumps({
                            'type': 'session_list',
                            'sessions': session_info
                        }))
                        continue

                    if not self.validate_tracking_point(message_data):
                        missing_fields = [
                            field for field in ["person", "sessionId", "latitude", "longitude", "distance", "currentSpeed", "averageSpeed"]
                            if field not in message_data
                        ]
                        logging.error(f"Missing required fields: {missing_fields}")
                        continue

                    # Create and store tracking point
                    tracking_point = self.create_tracking_point(message_data)
                    self.tracking_history[message_data['sessionId']].append(tracking_point)

                    # Broadcast update to all clients
                    await self.broadcast_update({
                        'type': 'update',
                        'point': tracking_point
                    })

                except json.JSONDecodeError as e:
                    if message != "ping":
                        logging.error(f"Invalid JSON received: {str(e)}")
                except Exception as e:
                    logging.error(f"Error processing message: {str(e)}")

        except websockets.exceptions.ConnectionClosed:
            logging.info(f"Client disconnected from {client_address}")
        except Exception as e:
            logging.error(f"Unexpected error in handle_client: {str(e)}")
        finally:
            if websocket in self.connected_clients:
                self.connected_clients.remove(websocket)
                logging.info(f"Removed client {client_address} from connected_clients")

async def main():
    """Main function to run the WebSocket server."""
    server = TrackingServer()

    logging.info("WebSocket server starting on port 6789")

    async with websockets.serve(server.handle_client, "0.0.0.0", 6789):
        try:
            await asyncio.Future()  # run forever
        except asyncio.CancelledError:
            pass

if __name__ == "__main__":
    asyncio.run(main())