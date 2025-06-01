#!/usr/bin/python
import asyncio
import datetime
import websockets
import json
import logging
import os
from collections import defaultdict
from typing import Set, DefaultDict, List, Dict, Any, Optional
import asyncpg
from dateutil import parser

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

        # Database connection pool
        self.db_pool: Optional[asyncpg.Pool] = None

        # Database configuration - use environment variables if available, fallback to defaults
        self.db_config = {
            'host': os.getenv('POSTGRES_HOST', 'localhost'),
            'port': int(os.getenv('POSTGRES_PORT', '8021')),
            'database': os.getenv('POSTGRES_DB', 'geotracker'),
            'user': os.getenv('POSTGRES_USER', 'geotracker'),
            'password': os.getenv('POSTGRES_PASSWORD', 'password')
        }

    async def init_database(self) -> None:
        """Initialize database connection pool."""
        try:
            self.db_pool = await asyncpg.create_pool(
                **self.db_config,
                min_size=2,
                max_size=10,
                command_timeout=60
            )
            logging.info("Database connection pool created successfully")

            # Test the connection
            async with self.db_pool.acquire() as conn:
                result = await conn.fetchval("SELECT 1")
                logging.info(f"Database connection test successful: {result}")

        except Exception as e:
            logging.error(f"Failed to initialize database connection: {str(e)}")
            raise

    async def close_database(self) -> None:
        """Close database connection pool."""
        if self.db_pool:
            await self.db_pool.close()
            logging.info("Database connection pool closed")

    async def save_tracking_data_to_db(self, message_data: Dict[str, Any]) -> bool:
        """Save tracking data to PostgreSQL database with additional user fields."""
        if not self.db_pool:
            logging.error("Database pool not initialized")
            return False

        try:
            # Parse the start_date_time if it exists, otherwise use current time
            start_date_time = None
            if 'startDateTime' in message_data:
                try:
                    start_date_time = parser.parse(message_data['startDateTime'])
                except Exception as e:
                    logging.warning(f"Could not parse startDateTime: {e}")
                    start_date_time = datetime.datetime.now()
            else:
                start_date_time = datetime.datetime.now()

            # Updated insert query with new fields INCLUDING person for backward compatibility and event fields
            insert_query = """
                INSERT INTO tracking_data (
                    session_id, person, firstname, lastname, birthdate, height, weight,
                    min_distance_meters, min_time_seconds, voice_announcement_interval,
                    event_name, sport_type, comment, clothing,
                    latitude, longitude, altitude, horizontal_accuracy, vertical_accuracy_meters,
                    number_of_satellites, satellites, used_number_of_satellites,
                    current_speed, average_speed, max_speed, moving_average_speed, speed,
                    speed_accuracy_meters_per_second, distance, covered_distance,
                    cumulative_elevation_gain, heart_rate, heart_rate_device, lap, start_date_time
                ) VALUES (
                    $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21,
                    $22, $23, $24, $25, $26, $27, $28, $29, $30, $31, $32, $33, $34, $35
                )
            """

            # Prepare parameters with defaults for missing fields
            params = [
                message_data.get('sessionId'),
                # Keep person column populated for backward compatibility
                message_data.get('firstname', message_data.get('person', '')),  # Use firstname or fallback to person
                # User profile data
                message_data.get('firstname', message_data.get('person', '')),  # fallback to 'person' for backward compatibility
                message_data.get('lastname', ''),
                message_data.get('birthdate', ''),
                float(message_data.get('height', 0)) if message_data.get('height') else None,
                float(message_data.get('weight', 0)) if message_data.get('weight') else None,
                # Settings data
                int(message_data.get('minDistanceMeters', 0)) if message_data.get('minDistanceMeters') else None,
                int(message_data.get('minTimeSeconds', 0)) if message_data.get('minTimeSeconds') else None,
                int(message_data.get('voiceAnnouncementInterval', 0)) if message_data.get('voiceAnnouncementInterval') else None,
                # Event/Session data
                message_data.get('eventName', ''),
                message_data.get('sportType', ''),
                message_data.get('comment', ''),
                message_data.get('clothing', ''),
                # Location data
                float(message_data.get('latitude', 0)),
                float(message_data.get('longitude', 0)),
                float(message_data.get('altitude', 0)) if message_data.get('altitude') is not None else None,
                float(message_data.get('horizontalAccuracy', 0)) if message_data.get('horizontalAccuracy') is not None else None,
                float(message_data.get('verticalAccuracyMeters', 0)) if message_data.get('verticalAccuracyMeters') is not None else None,
                # Satellite data
                int(message_data.get('numberOfSatellites', 0)) if message_data.get('numberOfSatellites') is not None else None,
                int(message_data.get('satellites', 0)) if message_data.get('satellites') is not None else None,
                int(message_data.get('usedNumberOfSatellites', 0)) if message_data.get('usedNumberOfSatellites') is not None else None,
                # Speed and movement data
                float(message_data.get('currentSpeed', 0)),
                float(message_data.get('averageSpeed', 0)),
                float(message_data.get('maxSpeed', 0)),
                float(message_data.get('movingAverageSpeed', 0)),
                float(message_data.get('speed', 0)) if message_data.get('speed') is not None else float(message_data.get('currentSpeed', 0)),
                float(message_data.get('speedAccuracyMetersPerSecond', 0)) if message_data.get('speedAccuracyMetersPerSecond') is not None else None,
                # Distance data
                float(message_data.get('distance', 0)),
                float(message_data.get('coveredDistance', 0)) if message_data.get('coveredDistance') is not None else float(message_data.get('distance', 0)),
                float(message_data.get('cumulativeElevationGain', 0)) if message_data.get('cumulativeElevationGain') is not None else None,
                # Health data
                int(message_data.get('heartRate', 0)) if message_data.get('heartRate') and message_data.get('heartRate') > 0 else None,
                message_data.get('heartRateDevice', '') if message_data.get('heartRateDevice') else None,
                # Session data
                int(message_data.get('lap', 0)) if message_data.get('lap') is not None else 0,
                start_date_time
            ]

            async with self.db_pool.acquire() as conn:
                await conn.execute(insert_query, *params)

            logging.info(f"Successfully saved tracking data to database for session {message_data.get('sessionId')}")
            return True

        except Exception as e:
            logging.error(f"Error saving tracking data to database: {str(e)}")
            logging.error(f"Data that failed to save: {json.dumps(message_data, indent=2)}")
            return False

    async def load_tracking_history_from_db(self) -> None:
        """Load recent tracking history from database on server startup."""
        if not self.db_pool:
            logging.warning("Database pool not initialized, skipping history load")
            return

        try:
            # Load tracking data from the last 24 hours
            cutoff_time = datetime.datetime.now() - datetime.timedelta(hours=24)

            query = """
                SELECT session_id, person, firstname, lastname, birthdate, height, weight,
                       min_distance_meters, min_time_seconds, voice_announcement_interval,
                       event_name, sport_type, comment, clothing,
                       latitude, longitude, altitude, current_speed, max_speed, 
                       moving_average_speed, average_speed, distance, heart_rate, 
                       heart_rate_device, received_at
                FROM tracking_data
                WHERE received_at >= $1
                ORDER BY session_id, received_at
            """

            async with self.db_pool.acquire() as conn:
                rows = await conn.fetch(query, cutoff_time)

            # Convert database rows to the format expected by the websocket clients
            for row in rows:
                tracking_point = {
                    "timestamp": row['received_at'].strftime(self.timestamp_format),
                    "sessionId": row['session_id'],
                    "firstname": row['firstname'] if row['firstname'] else row['person'],  # Use firstname if available, fallback to person
                    "lastname": row['lastname'] if row['lastname'] else '',
                    "birthdate": row['birthdate'] if row['birthdate'] else '',
                    "height": float(row['height']) if row['height'] is not None else 0.0,
                    "weight": float(row['weight']) if row['weight'] is not None else 0.0,
                    "minDistanceMeters": int(row['min_distance_meters']) if row['min_distance_meters'] is not None else 0,
                    "minTimeSeconds": int(row['min_time_seconds']) if row['min_time_seconds'] is not None else 0,
                    "voiceAnnouncementInterval": int(row['voice_announcement_interval']) if row['voice_announcement_interval'] is not None else 0,
                    "eventName": row['event_name'] if row['event_name'] else '',
                    "sportType": row['sport_type'] if row['sport_type'] else '',
                    "comment": row['comment'] if row['comment'] else '',
                    "clothing": row['clothing'] if row['clothing'] else '',
                    "latitude": float(row['latitude']),
                    "longitude": float(row['longitude']),
                    "altitude": float(row['altitude']) if row['altitude'] is not None else 0.0,
                    "currentSpeed": float(row['current_speed']) if row['current_speed'] else 0.0,
                    "maxSpeed": float(row['max_speed']) if row['max_speed'] else 0.0,
                    "movingAverageSpeed": float(row['moving_average_speed']) if row['moving_average_speed'] else 0.0,
                    "averageSpeed": float(row['average_speed']) if row['average_speed'] else 0.0,
                    "distance": float(row['distance']) if row['distance'] else 0.0,
                    # Keep 'person' for backward compatibility
                    "person": row['firstname'] if row['firstname'] else row['person']
                }

                # Add heart rate data if available
                if row['heart_rate'] and row['heart_rate'] > 0:
                    tracking_point["heartRate"] = int(row['heart_rate'])
                if row['heart_rate_device']:
                    tracking_point["heartRateDevice"] = row['heart_rate_device']

                self.tracking_history[row['session_id']].append(tracking_point)

            logging.info(f"Loaded {len(rows)} tracking points from database")

        except Exception as e:
            logging.error(f"Error loading tracking history from database: {str(e)}")

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
            "sessionId",
            "latitude",
            "longitude",
            "distance",
            "currentSpeed",
            "maxSpeed",
            "movingAverageSpeed",
            "averageSpeed"
        ]

        # Check for either 'firstname' or 'person' for backward compatibility
        has_name = "firstname" in message_data or "person" in message_data

        return has_name and all(
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

        # Create base tracking point with all fields
        tracking_point = {
            "timestamp": datetime.datetime.now().strftime(self.timestamp_format),
            **message_data,
            "currentSpeed": float(message_data["currentSpeed"]),
            "maxSpeed": float(message_data["maxSpeed"]),
            "movingAverageSpeed": float(message_data["movingAverageSpeed"]),
            "averageSpeed": float(message_data["averageSpeed"])
        }

        # Ensure backward compatibility with 'person' field
        if "firstname" in message_data and "person" not in message_data:
            tracking_point["person"] = message_data["firstname"]

        # Add heart rate data if available
        if "heartRate" in message_data:
            tracking_point["heartRate"] = int(message_data["heartRate"])

        if "heartRateDevice" in message_data:
            tracking_point["heartRateDevice"] = message_data["heartRateDevice"]

        return tracking_point

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

            # Delete from memory
            del self.tracking_history[session_id]
            if session_id in self.last_activity:
                del self.last_activity[session_id]

            # Delete from database
            if self.db_pool:
                try:
                    async with self.db_pool.acquire() as conn:
                        await conn.execute("DELETE FROM tracking_data WHERE session_id = $1", session_id)
                    logging.info(f"Deleted session from database: {session_id}")
                except Exception as e:
                    logging.error(f"Error deleting session from database: {str(e)}")
                    # Continue anyway, as we've already deleted from memory

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
                            field for field in ["sessionId", "latitude", "longitude", "distance", "currentSpeed", "averageSpeed"]
                            if field not in message_data
                        ]
                        has_name = "firstname" in message_data or "person" in message_data
                        if not has_name:
                            missing_fields.append("firstname or person")
                        logging.error(f"Missing required fields: {missing_fields}")
                        continue

                    # Save to database first (this is the new functionality)
                    db_success = await self.save_tracking_data_to_db(message_data)
                    if not db_success:
                        logging.warning("Failed to save to database, but continuing with in-memory storage")

                    # Create and store tracking point in memory (for real-time functionality)
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

    logging.info(f"WebSocket server starting on port 6789")
    logging.info(f"Database config: {server.db_config}")

    # Try to initialize database, but don't fail if it's not available
    try:
        await server.init_database()
        await server.load_tracking_history_from_db()
    except Exception as e:
        logging.error(f"Database initialization failed: {str(e)}")
        logging.info("Continuing without database - data will be stored in memory only")

    try:
        async with websockets.serve(server.handle_client, "0.0.0.0", 6789):
            try:
                await asyncio.Future()  # run forever
            except asyncio.CancelledError:
                pass
    finally:
        # Clean up database connections if they exist
        try:
            await server.close_database()
        except:
            pass

if __name__ == "__main__":
    asyncio.run(main())