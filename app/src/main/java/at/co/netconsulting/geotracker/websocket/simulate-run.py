import websocket
import json
import time
import math
import random
import ssl
from datetime import datetime
import hashlib
import threading
import sys

def generate_route(start_lat, start_lon, num_points=100):
    """Generate a more realistic running route with varied terrain."""
    points = []

    # Create a route that goes in a rough oval/loop
    for i in range(num_points):
        # Create an oval-shaped route
        angle = (i / num_points) * 2 * math.pi

        # Oval parameters
        a = 0.003  # Semi-major axis (roughly 300m)
        b = 0.002  # Semi-minor axis (roughly 200m)

        # Add some randomness to make it more realistic
        noise_factor = 0.0001
        noise_lat = random.uniform(-noise_factor, noise_factor)
        noise_lon = random.uniform(-noise_factor, noise_factor)

        lat = start_lat + a * math.cos(angle) + noise_lat
        lon = start_lon + b * math.sin(angle) + noise_lon

        points.append((lat, lon))

    return points

def calculate_distance(lat1, lon1, lat2, lon2):
    """Calculate distance between two points in meters using Haversine formula."""
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

def generate_session_id(person_name):
    """Generate a session ID similar to the Android app format."""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S%f")[:-3]
    random_hex = hashlib.md5(f"{person_name}{time.time()}".encode()).hexdigest()[:16]
    random_suffix = random.randint(100000, 999999)
    return f"{person_name}_{timestamp}_{random_hex}_{random_suffix}"

def calculate_elevation_gain(current_altitude, starting_altitude):
    """Calculate cumulative elevation gain."""
    return max(0, current_altitude - starting_altitude)

def create_websocket_connection(server_url, timeout=10):
    """Create a WebSocket connection with proper SSL context and error handling."""
    try:
        # Create SSL context that's more permissive
        ssl_context = ssl.create_default_context()
        ssl_context.check_hostname = False
        ssl_context.verify_mode = ssl.CERT_NONE

        print(f"Attempting to connect to {server_url}")

        # Create connection with longer timeout and SSL context
        ws = websocket.create_connection(
            server_url,
            timeout=timeout,
            sslopt={"context": ssl_context}
        )

        print("✓ WebSocket connection established")
        return ws

    except Exception as e:
        print(f"✗ Failed to connect to WebSocket: {str(e)}")
        return None

def send_message_with_retry(ws, message, max_retries=3):
    """Send a message with retry logic."""
    for attempt in range(max_retries):
        try:
            # Check if connection is still alive
            ws.ping()
            ws.send(json.dumps(message, separators=(',', ':')))
            return True
        except Exception as e:
            print(f"Send attempt {attempt + 1} failed: {str(e)}")
            if attempt < max_retries - 1:
                time.sleep(1)  # Wait before retry
            else:
                return False
    return False

def simulate_run(person_name="TestRunner", server_url="wss://geotracker.duckdns.org/geotracker"):
    """Simulate a running session with realistic data and robust error handling."""

    # Create WebSocket connection
    ws = create_websocket_connection(server_url)
    if not ws:
        print("Failed to establish WebSocket connection. Exiting.")
        return

    try:
        # Starting point (Vienna, Austria)
        start_lat, start_lon = 48.1818798, 16.3607528
        route = generate_route(start_lat, start_lon)

        # Initialize session
        session_id = generate_session_id(person_name)
        start_time = time.time()
        start_datetime = datetime.now().isoformat()

        # Tracking variables
        total_distance = 0.0
        last_lat, last_lon = route[0]
        max_speed_recorded = 0.0
        speed_queue = []
        starting_altitude = 200 + random.uniform(-20, 20)
        current_elevation_gain = 0.0
        lap = 0

        # Connection monitoring
        last_ping_time = time.time()
        ping_interval = 30  # Send ping every 30 seconds
        connection_failures = 0
        max_failures = 5

        print(f"Starting simulation for {person_name}")
        print(f"Session ID: {session_id}")
        print(f"Route has {len(route)} points")
        print("Press Ctrl+C to stop\n")

        for i, (lat, lon) in enumerate(route):
            # Check connection health periodically
            current_time = time.time()
            if current_time - last_ping_time > ping_interval:
                try:
                    ws.ping()
                    last_ping_time = current_time
                    connection_failures = 0  # Reset failure counter on successful ping
                except Exception as e:
                    print(f"⚠ Connection health check failed: {str(e)}")
                    connection_failures += 1

                    if connection_failures >= max_failures:
                        print("⚠ Too many connection failures. Attempting to reconnect...")
                        try:
                            ws.close()
                        except:
                            pass

                        ws = create_websocket_connection(server_url)
                        if not ws:
                            print("Failed to reconnect. Stopping simulation.")
                            break
                        connection_failures = 0
                        last_ping_time = current_time

            # Calculate distance increment
            if i > 0:
                distance_increment = calculate_distance(last_lat, last_lon, lat, lon)
                total_distance += distance_increment
                lap = int(total_distance // 1000)
            else:
                distance_increment = 0

            # Simulate realistic running speed
            base_speed = 10.0 + math.sin(i / 10) * 2
            speed_variation = random.uniform(-1.5, 1.5)
            current_speed = max(5.0, base_speed + speed_variation)

            max_speed_recorded = max(max_speed_recorded, current_speed)

            # Calculate moving average
            speed_queue.append(current_speed)
            if len(speed_queue) > 5:
                speed_queue.pop(0)
            moving_average_speed = sum(speed_queue) / len(speed_queue)

            # Calculate average speed since start
            elapsed_time_hours = (current_time - start_time) / 3600
            average_speed = (total_distance / 1000) / elapsed_time_hours if elapsed_time_hours > 0 else 0

            # Simulate altitude changes
            altitude_base = starting_altitude + math.sin(i / 15) * 15
            altitude_noise = random.uniform(-2, 2)
            current_altitude = altitude_base + altitude_noise
            current_elevation_gain = calculate_elevation_gain(current_altitude, starting_altitude)

            # Simulate satellite data
            satellites = random.randint(12, 20)
            used_satellites = int(satellites * random.uniform(0.6, 0.8))

            # Create the message in the flattened format the server expects (NO type wrapper, NO point nesting)
            message = {
                "sessionId": session_id,
                "latitude": lat,
                "longitude": lon,
                "altitude": current_altitude,
                "distance": total_distance,
                "currentSpeed": current_speed,
                "averageSpeed": average_speed,
                "maxSpeed": max_speed_recorded,
                "movingAverageSpeed": moving_average_speed,
                "coveredDistance": total_distance,
                "cumulativeElevationGain": current_elevation_gain,
                "heartRate": random.randint(120, 160) if random.random() > 0.1 else 0,
                "heartRateDevice": "SimulatedHR" if random.random() > 0.1 else "",
                "horizontalAccuracy": random.uniform(2.0, 8.0),
                "verticalAccuracyMeters": random.uniform(3.0, 6.0),
                "numberOfSatellites": satellites,
                "satellites": satellites,
                "usedNumberOfSatellites": used_satellites,
                "speed": current_speed,
                "speedAccuracyMetersPerSecond": random.uniform(0.2, 0.8),
                "startDateTime": start_datetime,
                "lap": lap,
                "person": person_name,
                "firstname": person_name,
                "lastname": "",
                "birthdate": "",
                "height": 0,
                "weight": 0
            }

            # Send the message with retry logic
            if not send_message_with_retry(ws, message):
                print(f"⚠ Failed to send message for point {i+1}. Attempting to reconnect...")
                try:
                    ws.close()
                except:
                    pass

                ws = create_websocket_connection(server_url)
                if not ws:
                    print("Failed to reconnect. Stopping simulation.")
                    break

                # Try sending the message again with the new connection
                if not send_message_with_retry(ws, message):
                    print("Failed to send message even after reconnect. Skipping this point.")

            # Progress output
            distance_km = total_distance / 1000
            status_symbol = "📍" if i % 10 == 0 else "•"
            print(f"{status_symbol} Point {i+1:3d}/{len(route)}: {distance_km:.3f}km, "
                  f"{current_speed:.1f} km/h, {satellites} sats, "
                  f"alt: {current_altitude:.1f}m")

            last_lat, last_lon = lat, lon

            # Send updates every 2-3 seconds (more realistic and less server load)
            time.sleep(random.uniform(2.0, 3.0))

    except KeyboardInterrupt:
        print(f"\n🏁 Stopping simulation for {person_name}...")
        print(f"Total distance: {total_distance/1000:.2f} km")
        print(f"Max speed: {max_speed_recorded:.1f} km/h")
        print(f"Average speed: {average_speed:.1f} km/h")
        print(f"Total time: {(time.time() - start_time)/60:.1f} minutes")
    except Exception as e:
        print(f"⚠ Error during simulation: {str(e)}")
        import traceback
        traceback.print_exc()
    finally:
        try:
            if ws:
                ws.close()
                print("🔌 WebSocket connection closed")
        except:
            pass

def simulate_multiple_runners(num_runners=2):
    """Simulate multiple runners for testing."""
    print(f"🏃 Starting simulation with {num_runners} runners")

    threads = []
    for i in range(num_runners):
        runner_name = f"TestRunner{i+1}"
        thread = threading.Thread(target=simulate_run, args=(runner_name,))
        threads.append(thread)
        thread.start()

        # Stagger the start times to avoid overwhelming the server
        time.sleep(5)

    print(f"All {num_runners} runners started. Press Ctrl+C to stop all simulations.")

    # Wait for all threads to complete
    try:
        for thread in threads:
            thread.join()
    except KeyboardInterrupt:
        print("\n🛑 Stopping all simulations...")

if __name__ == "__main__":
    print("🎯 GPS Tracking Simulator")
    print("=" * 40)

    if len(sys.argv) > 1:
        if sys.argv[1] == "multi":
            num_runners = int(sys.argv[2]) if len(sys.argv) > 2 else 2
            simulate_multiple_runners(num_runners)
        else:
            person_name = sys.argv[1]
            simulate_run(person_name)
    else:
        # Default single runner
        simulate_run("TestRunner")
