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
        self.retention_hours = 24
        self.cleanup_interval = 30  # minutes
        self.timestamp_format = '%d-%m-%Y %H:%M:%S'
        self.batch_size = 100

    def clean_old_data(self) -> None:
        """Remove data points older than retention_hours."""
        try:
            cutoff_time = datetime.datetime.now() - datetime.timedelta(hours=self.retention_hours)
            cleaned_count = 0

            for session_id in list(self.tracking_history.keys()):
                original_length = len(self.tracking_history[session_id])
                self.tracking_history[session_id] = [
                    point for point in self.tracking_history[session_id]
                    if datetime.datetime.strptime(point['timestamp'], self.timestamp_format) > cutoff_time
                ]
                cleaned_count += original_length - len(self.tracking_history[session_id])

                # Remove empty sessions
                if not self.tracking_history[session_id]:
                    del self.tracking_history[session_id]

            if cleaned_count > 0:
                logging.info(f"Cleaned {cleaned_count} old data points")

        except Exception as e:
            logging.error(f"Error during data cleanup: {str(e)}")

    async def periodic_cleanup(self) -> None:
        """Periodically clean old data."""
        while True:
            await asyncio.sleep(self.cleanup_interval * 60)  # Convert minutes to seconds
            self.clean_old_data()

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
            self.clean_old_data()

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
            "averageSpeed"
        ]

        return all(
            key in message_data and message_data[key] is not None
            for key in required_fields
        )

    def create_tracking_point(self, message_data: Dict[str, Any]) -> Dict[str, Any]:
        """Create a tracking point with current timestamp."""
        return {
            "timestamp": datetime.datetime.now().strftime(self.timestamp_format),
            **message_data,
            "averageSpeed": float(message_data["averageSpeed"])
        }

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

                    message_data = json.loads(message)

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
    cleanup_task = asyncio.create_task(server.periodic_cleanup())

    logging.info("WebSocket server starting on port 6789")

    async with websockets.serve(server.handle_client, "0.0.0.0", 6789):
        try:
            await asyncio.Future()  # run forever
        finally:
            cleanup_task.cancel()
            try:
                await cleanup_task
            except asyncio.CancelledError:
                pass

if __name__ == "__main__":
    asyncio.run(main())