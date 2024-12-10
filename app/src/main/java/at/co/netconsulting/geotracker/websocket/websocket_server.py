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
        self.tracking_history = defaultdict(list)  # Store data by session
        self.retention_hours = 24

    def clean_old_data(self):
        """Remove data older than retention_hours"""
        cutoff_time = datetime.datetime.now() - datetime.timedelta(hours=self.retention_hours)
        for session_id in list(self.tracking_history.keys()):
            self.tracking_history[session_id] = [
                point for point in self.tracking_history[session_id]
                if datetime.datetime.strptime(point['timestamp'], '%d-%m-%Y %H:%M:%S') > cutoff_time
            ]
            if not self.tracking_history[session_id]:
                del self.tracking_history[session_id]

    async def send_history(self, websocket):
        """Send historical data to newly connected client"""
        for session_id, points in self.tracking_history.items():
            for point in points:
                await websocket.send(json.dumps(point))

    async def handle_client(self, websocket):
        self.connected_clients.add(websocket)
        client_address = websocket.remote_address
        logging.info(f"New client connected from {client_address}")

        try:
            # Send historical data to new client
            await self.send_history(websocket)

            async for message in websocket:
                logging.info(f"Received message: {message}")
                message_data = json.loads(message)

                if all(key in message_data for key in ("person", "sessionId", "latitude", "longitude", "distance", "currentSpeed")):
                    # Add timestamp and store in history
                    tracking_point = {
                        "timestamp": datetime.datetime.now().strftime('%d-%m-%Y %H:%M:%S'),
                        **message_data
                    }
                    self.tracking_history[message_data['sessionId']].append(tracking_point)

                    # Clean old data
                    self.clean_old_data()

                    # Broadcast to all connected clients
                    for client in self.connected_clients:
                        await client.send(json.dumps(tracking_point))

        except websockets.exceptions.ConnectionClosed:
            logging.info(f"Client disconnected from {client_address}")
        finally:
            self.connected_clients.remove(websocket)

async def main():
    server = TrackingServer()
    logging.info("WebSocket server starting on port 6789")
    async with websockets.serve(server.handle_client, "0.0.0.0", 6789):
        await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(main())
