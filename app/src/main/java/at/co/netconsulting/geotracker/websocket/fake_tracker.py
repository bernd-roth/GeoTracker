#!/usr/bin/env python3
"""
Fake tracker script to simulate a user recording a GPS session.
Useful for testing the "Follow Active Users" feature.

Usage:
    python fake_tracker.py --server your-server.com --name "Test User"
"""

import asyncio
import websockets
import json
import argparse
import random
import math
from datetime import datetime

async def simulate_tracking(server_url: str, person_name: str, event_name: str, duration_seconds: int, speed_ms: float = 3.5, lap_distance: float = 1.0):
    """Simulate a tracking session by sending fake GPS data."""

    # Generate a unique session ID
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S%f")[:-3]
    random_hex = ''.join(random.choices('0123456789abcdef', k=16))
    random_num = random.randint(100000, 999999)
    session_id = f"{person_name.replace(' ', '')}_{timestamp}_{random_hex}_{random_num}"

    # Starting position (Lund, Sweden area - same as your test data)
    lat = 55.7151
    lon = 13.2342
    altitude = 110.0
    distance = 0.0

    # Movement parameters
    speed = speed_ms  # m/s
    bearing = random.uniform(0, 360)  # Initial random direction

    # Lap tracking
    lap_distance_km = lap_distance
    lap_times = []  # Accumulated lap data
    current_lap_start_ms = 0  # ms elapsed when current lap started
    elapsed_ms = 0  # Total elapsed time in ms

    print(f"Connecting to wss://{server_url}/geotracker")
    print(f"Session ID: {session_id}")
    print(f"Person: {person_name}")
    print(f"Event: {event_name}")
    print(f"Duration: {duration_seconds} seconds")
    print(f"Speed: {speed_ms:.1f} m/s ({speed_ms * 3.6:.1f} km/h)")
    print(f"Lap distance: {lap_distance} km (first lap at ~{lap_distance * 1000 / speed_ms:.0f}s)")
    print("-" * 50)

    websocket = None

    async def connect():
        """Establish websocket connection with retry."""
        return await websockets.connect(
            f"wss://{server_url}/geotracker",
            ping_interval=10,
            ping_timeout=30,
            close_timeout=5
        )

    async def send_point(ws, point):
        """Send a tracking point, reconnecting if needed."""
        nonlocal websocket
        try:
            await ws.send(json.dumps(point))
            return ws
        except (websockets.exceptions.ConnectionClosed,
                websockets.exceptions.ConnectionClosedError,
                websockets.exceptions.ConnectionClosedOK) as e:
            print(f"\nConnection lost: {e}. Reconnecting...")
            try:
                websocket = await connect()
                await websocket.send(json.dumps(point))
                print("Reconnected successfully!")
                return websocket
            except Exception as reconnect_error:
                print(f"Failed to reconnect: {reconnect_error}")
                raise

    try:
        websocket = await connect()
        print("Connected to WebSocket server")

        start_time = datetime.now()
        point_count = 0

        while point_count < duration_seconds:
            # Calculate new position (simple movement simulation)
            bearing += random.uniform(-15, 15)
            bearing = bearing % 360

            bearing_rad = math.radians(bearing)

            delta_lat = (speed * math.cos(bearing_rad)) / 111000
            delta_lon = (speed * math.sin(bearing_rad)) / (111000 * math.cos(math.radians(lat)))

            lat += delta_lat
            lon += delta_lon
            altitude += random.uniform(-0.5, 0.5)
            distance += speed
            elapsed_ms += 1000  # 1 second per iteration

            # Check for lap completion
            distance_km = distance / 1000.0
            expected_laps = int(distance_km / lap_distance_km)
            while len(lap_times) < expected_laps:
                lap_number = len(lap_times) + 1
                # Duration varies per lap to make fastest/slowest highlighting visible
                lap_duration_ms = elapsed_ms - current_lap_start_ms + random.randint(-15000, 15000)
                lap_duration_ms = max(lap_duration_ms, 180000)  # At least 3 minutes
                lap_start_ms = current_lap_start_ms
                lap_end_ms = lap_start_ms + lap_duration_ms
                lap_times.append({
                    "lapNumber": lap_number,
                    "duration": lap_duration_ms,
                    "distance": lap_distance_km,
                    "startTime": lap_start_ms,
                    "endTime": lap_end_ms
                })
                current_lap_start_ms = elapsed_ms
                print(f"  >> Lap {lap_number} completed: {lap_duration_ms / 1000:.0f}s")

            current_speed = speed + random.uniform(-0.5, 0.5)

            tracking_point = {
                "altitude": altitude,
                "altitudeFromPressure": altitude + random.uniform(-2, 2),
                "averageSlope": random.uniform(-1, 1),
                "averageSpeed": speed,
                "bearing": bearing,
                "birthdate": "01.01.1990",
                "clothing": "",
                "comment": "Simulated session for testing",
                "coveredDistance": distance,
                "cumulativeElevationGain": max(0, random.uniform(-5, 10)),
                "currentDateTime": datetime.now().isoformat(),
                "currentSpeed": current_speed,
                "distance": distance,
                "eventName": event_name,
                "firstname": person_name,
                "heartRate": random.randint(60, 120),
                "heartRateDevice": "",
                "height": 175.0,
                "horizontalAccuracy": random.uniform(2, 5),
                "lap": 0,
                "lastname": "Tester",
                "latitude": lat,
                "longitude": lon,
                "maxDownhillSlope": random.uniform(0, 5),
                "maxSpeed": speed + 1,
                "maxUphillSlope": random.uniform(0, 5),
                "minDistanceMeters": 1,
                "minTimeSeconds": 1,
                "movingAverageSpeed": speed,
                "numberOfSatellites": random.randint(15, 25),
                "person": person_name,
                "pressure": 1013.25 + random.uniform(-5, 5),
                "pressureAccuracy": 3,
                "relativeHumidity": random.randint(40, 80),
                "satellites": random.randint(15, 25),
                "seaLevelPressure": 1013.25,
                "sessionId": session_id,
                "slope": random.uniform(-2, 2),
                "speed": current_speed,
                "speedAccuracyMetersPerSecond": random.uniform(0.1, 0.3),
                "sportType": "Running",
                "startDateTime": start_time.isoformat(),
                "temperature": random.uniform(-2, 5),
                "timezoneOffsetHours": 1,
                "usedNumberOfSatellites": random.randint(10, 20),
                "verticalAccuracyMeters": random.uniform(2, 5),
                "voiceAnnouncementInterval": 10,
                "weatherCode": 3,
                "weatherTime": datetime.now().strftime("%Y-%m-%dT%H:%M"),
                "weight": 70.0,
                "windDirection": random.uniform(0, 360),
                "windSpeed": random.uniform(0, 20),
                "lapTimes": lap_times if lap_times else None
            }

            # Send the tracking point with auto-reconnect
            websocket = await send_point(websocket, tracking_point)
            point_count += 1

            print(f"[{point_count}/{duration_seconds}] Sent point: lat={lat:.6f}, lon={lon:.6f}, distance={distance:.1f}m")

            await asyncio.sleep(1)

        print("-" * 50)
        print(f"Simulation complete! Sent {point_count} points over {distance:.1f}m")
        print(f"Session ID: {session_id}")
        print("The session will remain active as long as this script runs.")

        # Keep connection alive
        print("\nKeeping session alive (press Ctrl+C to stop)...")
        while True:
            try:
                await asyncio.sleep(2)
                tracking_point["currentDateTime"] = datetime.now().isoformat()
                tracking_point["latitude"] = lat + random.uniform(-0.00001, 0.00001)
                tracking_point["longitude"] = lon + random.uniform(-0.00001, 0.00001)
                websocket = await send_point(websocket, tracking_point)
                print(".", end="", flush=True)
            except KeyboardInterrupt:
                print("\nStopping...")
                break
            except Exception as e:
                print(f"\nError during keepalive: {e}")
                break

    except KeyboardInterrupt:
        print("\nStopped by user")
    except Exception as e:
        print(f"Error: {e}")
    finally:
        if websocket:
            try:
                await websocket.close()
            except:
                pass

def main():
    parser = argparse.ArgumentParser(description="Simulate a GPS tracking session")
    parser.add_argument("--server", required=True, help="WebSocket server hostname (e.g., your-server.com)")
    parser.add_argument("--name", default="FakeUser", help="Name of the simulated user")
    parser.add_argument("--event", default="Test Event", help="Event name")
    parser.add_argument("--duration", type=int, default=600, help="Duration in seconds (default: 600)")
    parser.add_argument("--speed", type=float, default=3.5, help="Speed in m/s (default: 3.5, ~12.6 km/h running)")
    parser.add_argument("--lap-distance", type=float, default=1.0, help="Lap distance in km (default: 1.0)")

    args = parser.parse_args()

    asyncio.run(simulate_tracking(args.server, args.name, args.event, args.duration, args.speed, args.lap_distance))

if __name__ == "__main__":
    main()
