#!/usr/bin/python
import asyncio
import datetime
import websockets
import json
import logging

logging.basicConfig(filename='/app/logs/websocket.log', level=logging.INFO)
connected_clients = set()

async def handle_client(websocket):
connected_clients.add(websocket)
logging.info(f"New client connected")
logging.info(f"WebSocket server started on port 6789")
logging.info(f"Client connected from {websocket.remote_address}")

try:
async for message in websocket:
logging.info(f"Received message: {message}")
message_data = json.loads(message)

if all(key in message_data for key in ("person", "sessionId", "latitude", "longitude", "distance", "currentSpeed")):
for client in connected_clients:
await client.send(json.dumps({
    "timestamp": datetime.datetime.now().strftime('%d-%m-%Y %H:%M:%S'),
    **message_data
}))
except websockets.exceptions.ConnectionClosed:
logging.info("Client disconnected")
finally:
connected_clients.remove(websocket)

async def main():
async with websockets.serve(handle_client, "0.0.0.0", 6789):
await asyncio.Future()

if __name__ == "__main__":
asyncio.run(main())