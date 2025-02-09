import websocket
import json
import time
import math
import random
from datetime import datetime

def generate_route(start_lat, start_lon, num_points=70):
    """Generate a circular-ish running route."""
    points = []
    radius = 0.005  # Roughly 500m radius
    for i in range(num_points):
        angle = (i / num_points) * 2 * math.pi
        # Add some random variation to make it more realistic
        r = radius * (1 + random.uniform(-0.1, 0.1))
        lat = start_lat + r * math.cos(angle) + 0.5
        lon = start_lon + r * math.sin(angle) + 0.5
        points.append((lat, lon))
    return points

def calculate_distance(lat1, lon1, lat2, lon2):
    """Calculate distance between two points in meters."""
    R = 6371000  # Earth's radius in meters
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = math.sin(delta_phi/2) * math.sin(delta_phi/2) + \
        math.cos(phi1) * math.cos(phi2) * \
        math.sin(delta_lambda/2) * math.sin(delta_lambda/2)
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
    return R * c

def simulate_run():
    ws = websocket.create_connection("ws://62.178.111.184:8011/runningtracker")
    # Starting point (Vienna, Austria)
    start_lat, start_lon = 48.1818798, 16.3607528
    route = generate_route(start_lat, start_lon)

    # Initial values
    total_distance = 0
    last_lat, last_lon = route[0]
    start_time = time.time()
    person_id = f"test_runner_{int(time.time())}"
    max_speed_recorded = 0  # Track max speed
    speed_queue = []  # For moving average

    try:
        for i, (lat, lon) in enumerate(route):
            point_distance = calculate_distance(last_lat, last_lon, lat, lon)
            total_distance += point_distance

            # Calculate speeds
            base_speed = 10  # 10 km/h base speed
            current_speed = base_speed + random.uniform(-2, 2)

            # Update max speed
            max_speed_recorded = max(max_speed_recorded, current_speed)

            # Calculate moving average
            speed_queue.append(current_speed)
            if len(speed_queue) > 5:  # Keep last 5 speeds
                speed_queue.pop(0)
            moving_average = sum(speed_queue) / len(speed_queue)

            # Calculate average speed
            elapsed_time = time.time() - start_time
            average_speed = (total_distance / 1000) / (elapsed_time / 3600) if elapsed_time > 0 else 0

            # Simulate altitude
            altitude = 175 + math.sin(i / 10) * 25

            # Create data packet
            data = {
                "person": person_id,
                "sessionId": person_id,
                "latitude": lat,
                "longitude": lon,
                "altitude": str(altitude),  # Convert to string to match your format
                "currentSpeed": current_speed,
                "maxSpeed": max_speed_recorded,
                "movingAverageSpeed": moving_average,
                "averageSpeed": average_speed,
                "distance": str(total_distance),  # Convert to string to match your format
                "formattedTimestamp": datetime.now().strftime("%d-%m-%Y %H:%M:%S"),
                "totalAscent": random.uniform(10, 20),  # Simulated values
                "totalDescent": random.uniform(10, 20)  # Simulated values
            }

            ws.send(json.dumps(data))
            print(f"Sent point {i+1}/{len(route)}: {lat}, {lon}")

            last_lat, last_lon = lat, lon
            time.sleep(0.5)

    except KeyboardInterrupt:
        print("\nStopping simulation...")
    except Exception as e:
        print(f"Error during simulation: {str(e)}")
    finally:
        ws.close()

if __name__ == "__main__":
    simulate_run()