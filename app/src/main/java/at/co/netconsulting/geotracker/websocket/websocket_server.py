#!/usr/bin/python
import asyncio
import datetime
import websockets
import json
import logging
from collections import defaultdict

logging.basicConfig(filename='/app/logs/websocket.log', level=logging.INFO)

class TrackingServer:
    def __init__(self):
        self.connected_clients = set()
        self.tracking_history = defaultdict(list)
        self.retention_hours = 24
        self.cleanup_interval = 30
        self.timestamp_format = '%d-%m-%Y %H:%M:%S'
        self.batch_size = 100  # Number of points to send in each batch

    def clean_old_data(self):
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

                if not self.tracking_history[session_id]:
                    del self.tracking_history[session_id]

            if cleaned_count > 0:
                logging.info(f"Cleaned {cleaned_count} old data points")

        except Exception as e:
            logging.error(f"Error during data cleanup: {str(e)}")

    async def periodic_cleanup(self):
        while True:
            await asyncio.sleep(self.cleanup_interval)
            self.clean_old_data()

    async def send_history(self, websocket):
        """Send historical data to newly connected client in batches"""
        try:
            self.clean_old_data()

            # Prepare all points from all sessions
            all_points = []
            for session_id, points in self.tracking_history.items():
                all_points.extend(points)

            # Sort points by timestamp
            all_points.sort(key=lambda x: datetime.datetime.strptime(x['timestamp'], self.timestamp_format))

            # Send points in batches
            for i in range(0, len(all_points), self.batch_size):
                batch = all_points[i:i + self.batch_size]
                await websocket.send(json.dumps({
                    'type': 'batch',
                    'points': batch
                }))
                # Small delay between batches to prevent overwhelming the client
                await asyncio.sleep(0.05)

            # Send completion message
            await websocket.send(json.dumps({
                'type': 'history_complete'
            }))

        except Exception as e:
            logging.error(f"Error sending history: {str(e)}")

    async def handle_client(self, websocket):
        self.connected_clients.add(websocket)
        client_address = websocket.remote_address
        logging.info(f"New client connected from {client_address}")

        try:
            await self.send_history(websocket)

            async for message in websocket:
                try:
                    if message == "ping":
                        await websocket.send("pong")
                        continue

                    message_data = json.loads(message)
                    required_fields = ["person", "sessionId", "latitude", "longitude",
                                       "distance", "currentSpeed", "averageSpeed"]

                    if all(key in message_data for key in required_fields):
                        tracking_point = {
                            "timestamp": datetime.datetime.now().strftime(self.timestamp_format),
                            **message_data,
                            "averageSpeed": float(message_data["averageSpeed"])
                        }

                        self.tracking_history[message_data['sessionId']].append(tracking_point)

                        # Send real-time updates individually
                        websockets_to_remove = set()
                        for client in self.connected_clients:
                            try:
                                await client.send(json.dumps({
                                    'type': 'update',
                                    'point': tracking_point
                                }))
                            except websockets.exceptions.ConnectionClosed:
                                websockets_to_remove.add(client)

                        self.connected_clients -= websockets_to_remove

                    else:
                        missing_fields = [field for field in required_fields if field not in message_data]
                        logging.error(f"Missing required fields: {missing_fields}")

                except json.JSONDecodeError as e:
                    if message != "ping":
                        logging.error(f"Invalid JSON received: {str(e)}")
                except Exception as e:
                    logging.error(f"Error processing message: {str(e)}")

        except websockets.exceptions.ConnectionClosed:
            logging.info(f"Client disconnected from {client_address}")
        finally:
            self.connected_clients.remove(websocket)

async def main():
    server = TrackingServer()
    cleanup_task = asyncio.create_task(server.periodic_cleanup())

    logging.info("WebSocket server starting on port 6789")
    async with websockets.serve(server.handle_client, "0.0.0.0", 6789):
        try:
            await asyncio.Future()
        finally:
            cleanup_task.cancel()
            try:
                await cleanup_task
            except asyncio.CancelledError:
                pass

if __name__ == "__main__":
    asyncio.run(main())