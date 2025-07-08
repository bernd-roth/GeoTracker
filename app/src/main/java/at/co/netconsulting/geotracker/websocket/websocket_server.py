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

        # CONFIGURABLE CLEANUP SETTINGS
        # Data retention period in hours - configurable via environment variable or script modification
        self.data_retention_hours = int(os.getenv('DATA_RETENTION_HOURS', '48'))  # Default: 48 hours

        # Cleanup interval in seconds - how often to run cleanup
        self.cleanup_interval_seconds = int(os.getenv('CLEANUP_INTERVAL_SECONDS', '3600'))  # Default: 1 hour

        # Enable/disable automatic cleanup
        self.enable_automatic_cleanup = os.getenv('ENABLE_AUTOMATIC_CLEANUP', 'true').lower() == 'true'

        # Following system
        self.client_following: Dict[websockets.WebSocketServerProtocol, Set[str]] = {}  # client -> set of session_ids
        self.session_followers: DefaultDict[str, Set[websockets.WebSocketServerProtocol]] = defaultdict(set)  # session_id -> set of clients

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

        logging.info(f"Memory cleanup configuration: retention={self.data_retention_hours}h, interval={self.cleanup_interval_seconds}s, auto_cleanup={self.enable_automatic_cleanup}")

    async def cleanup_old_data_from_memory(self) -> None:
        """Remove tracking data older than retention period from memory ONLY (database is untouched)."""
        try:
            # Calculate cutoff time
            cutoff_time = datetime.datetime.now() - datetime.timedelta(hours=self.data_retention_hours)
            removed_points = 0
            sessions_to_remove = []

            logging.info(f"Starting memory cleanup: removing data older than {cutoff_time.strftime(self.timestamp_format)} (retention: {self.data_retention_hours} hours)")

            for session_id, points in self.tracking_history.items():
                # Filter out old points
                filtered_points = []
                for point in points:
                    try:
                        # Parse timestamp from the point
                        point_time = datetime.datetime.strptime(point['timestamp'], self.timestamp_format)

                        if point_time >= cutoff_time:
                            filtered_points.append(point)
                        else:
                            removed_points += 1
                    except Exception as e:
                        logging.warning(f"Error parsing timestamp for cleanup in session {session_id}: {e}")
                        # Keep the point if we can't parse its timestamp
                        filtered_points.append(point)

                # Update the session's points or mark for removal
                if filtered_points:
                    self.tracking_history[session_id] = filtered_points
                else:
                    sessions_to_remove.append(session_id)

            # Remove empty sessions from memory
            for session_id in sessions_to_remove:
                del self.tracking_history[session_id]
                if session_id in self.last_activity:
                    del self.last_activity[session_id]
                # Don't remove from active_sessions if it's still actually active
                if session_id in self.active_sessions:
                    # Check if session is truly inactive before removing
                    self.update_active_sessions()

            if removed_points > 0 or sessions_to_remove:
                logging.info(f"Memory cleanup completed: removed {removed_points} old points and {len(sessions_to_remove)} empty sessions from memory")

                # Broadcast updated session list to all clients
                await self.broadcast_session_list_update()
            else:
                logging.info(f"Memory cleanup completed: no old data found (retention: {self.data_retention_hours} hours)")

        except Exception as e:
            logging.error(f"Error during memory cleanup: {str(e)}")

    async def manual_cleanup_memory(self) -> Dict[str, Any]:
        """Manually trigger memory cleanup and return result."""
        try:
            await self.cleanup_old_data_from_memory()
            return {"success": True, "message": f"Memory cleanup completed (retention: {self.data_retention_hours} hours)"}
        except Exception as e:
            logging.error(f"Manual memory cleanup failed: {str(e)}")
            return {"success": False, "message": f"Memory cleanup failed: {str(e)}"}

    async def broadcast_session_list_update(self) -> None:
        """Broadcast updated session list to all clients."""
        try:
            self.update_active_sessions()
            session_info = [
                {
                    "sessionId": session_id,
                    "isActive": session_id in self.active_sessions
                }
                for session_id in self.tracking_history.keys()
            ]

            await self.broadcast_update({
                'type': 'session_list',
                'sessions': session_info
            })

            logging.info(f"Broadcasted session list update: {len(session_info)} sessions")

        except Exception as e:
            logging.error(f"Error broadcasting session list update: {str(e)}")

    async def periodic_cleanup_task(self) -> None:
        """Background task that runs periodic memory cleanup."""
        if not self.enable_automatic_cleanup:
            logging.info("Automatic cleanup is disabled")
            return

        logging.info(f"Starting periodic memory cleanup task: every {self.cleanup_interval_seconds} seconds, retention {self.data_retention_hours} hours")

        while True:
            try:
                await asyncio.sleep(self.cleanup_interval_seconds)
                await self.cleanup_old_data_from_memory()
            except asyncio.CancelledError:
                logging.info("Periodic cleanup task cancelled")
                break
            except Exception as e:
                logging.error(f"Error in periodic cleanup task: {str(e)}")

    async def init_database(self) -> None:
        """Initialize database connection pool and create normalized tables."""
        try:
            self.db_pool = await asyncpg.create_pool(
                **self.db_config,
                min_size=2,
                max_size=10,
                command_timeout=60
            )
            logging.info("Database connection pool created successfully")

            # Create normalized tables
            await self.create_normalized_tables()

            # Test the connection
            async with self.db_pool.acquire() as conn:
                result = await conn.fetchval("SELECT 1")
                logging.info(f"Database connection test successful: {result}")

        except Exception as e:
            logging.error(f"Failed to initialize database connection: {str(e)}")
            raise

    async def create_normalized_tables(self) -> None:
        """Create the normalized database tables if they don't exist."""
        async with self.db_pool.acquire() as conn:
            # Create users table
            await conn.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id SERIAL PRIMARY KEY,
                    firstname VARCHAR(100) NOT NULL,
                    lastname VARCHAR(100),
                    birthdate VARCHAR(20),
                    height NUMERIC(5, 2),
                    weight NUMERIC(5, 2),
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    updated_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(firstname, lastname, birthdate)
                )
            """)

            # Create heart_rate_devices table
            await conn.execute("""
                CREATE TABLE IF NOT EXISTS heart_rate_devices (
                    device_id SERIAL PRIMARY KEY,
                    device_name VARCHAR(100) UNIQUE NOT NULL,
                    created_at TIMESTAMPTZ DEFAULT NOW()
                )
            """)

            # Create tracking_sessions table
            await conn.execute("""
                CREATE TABLE IF NOT EXISTS tracking_sessions (
                    session_id VARCHAR(255) PRIMARY KEY,
                    user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
                    event_name VARCHAR(255),
                    sport_type VARCHAR(100),
                    comment TEXT,
                    clothing VARCHAR(255),
                    start_date_time TIMESTAMPTZ,
                    min_distance_meters INTEGER,
                    min_time_seconds INTEGER,
                    voice_announcement_interval INTEGER,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    updated_at TIMESTAMPTZ DEFAULT NOW()
                )
            """)

            # Create gps_tracking_points table with weather columns
            await conn.execute("""
                CREATE TABLE IF NOT EXISTS gps_tracking_points (
                    id SERIAL PRIMARY KEY,
                    session_id VARCHAR(255) REFERENCES tracking_sessions(session_id) ON DELETE CASCADE,
                    latitude NUMERIC(10, 8) NOT NULL,
                    longitude NUMERIC(11, 8) NOT NULL,
                    altitude NUMERIC(10, 4),
                    horizontal_accuracy NUMERIC(8, 4),
                    vertical_accuracy_meters NUMERIC(8, 4),
                    number_of_satellites INTEGER,
                    satellites INTEGER,
                    used_number_of_satellites INTEGER,
                    current_speed NUMERIC(8, 4) NOT NULL,
                    average_speed NUMERIC(8, 4) NOT NULL,
                    max_speed NUMERIC(8, 4) NOT NULL,
                    moving_average_speed NUMERIC(8, 4) NOT NULL,
                    speed NUMERIC(8, 4),
                    speed_accuracy_meters_per_second NUMERIC(8, 4),
                    distance NUMERIC(12, 4) NOT NULL,
                    covered_distance NUMERIC(12, 4),
                    cumulative_elevation_gain NUMERIC(10, 4),
                    heart_rate INTEGER,
                    heart_rate_device_id INTEGER REFERENCES heart_rate_devices(device_id),
                    lap INTEGER DEFAULT 0,
                    temperature NUMERIC(5, 2),
                    wind_speed NUMERIC(6, 2),
                    wind_direction VARCHAR(3),
                    humidity INTEGER,
                    weather_timestamp BIGINT,
                    weather_code INTEGER,
                    received_at TIMESTAMPTZ DEFAULT NOW(),
                    created_at TIMESTAMPTZ DEFAULT NOW()
                )
            """)

            # Create indexes for performance
            await conn.execute("CREATE INDEX IF NOT EXISTS idx_gps_session_id ON gps_tracking_points(session_id)")
            await conn.execute("CREATE INDEX IF NOT EXISTS idx_gps_received_at ON gps_tracking_points(received_at)")
            await conn.execute("CREATE INDEX IF NOT EXISTS idx_gps_location ON gps_tracking_points(latitude, longitude)")
            await conn.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON tracking_sessions(user_id)")
            await conn.execute("CREATE INDEX IF NOT EXISTS idx_users_name ON users(firstname, lastname)")
            await conn.execute("CREATE INDEX IF NOT EXISTS idx_gps_temperature ON gps_tracking_points(temperature) WHERE temperature IS NOT NULL")
            await conn.execute("CREATE INDEX IF NOT EXISTS idx_gps_wind_speed ON gps_tracking_points(wind_speed) WHERE wind_speed IS NOT NULL")

            logging.info("Normalized database tables created successfully")

    async def close_database(self) -> None:
        """Close database connection pool."""
        if self.db_pool:
            await self.db_pool.close()
            logging.info("Database connection pool closed")

    async def get_or_create_user(self, conn, firstname: str, lastname: str = None,
                                 birthdate: str = None, height: float = None,
                                 weight: float = None) -> int:
        """Get existing user or create new one, return user_id."""
        # Try to find existing user
        user_id = await conn.fetchval("""
            SELECT user_id FROM users
            WHERE firstname = $1 AND COALESCE(lastname, '') = COALESCE($2, '')
            AND COALESCE(birthdate, '') = COALESCE($3, '')
        """, firstname, lastname or '', birthdate or '')

        if user_id:
            # Update user info if provided and different
            if height is not None or weight is not None:
                await conn.execute("""
                    UPDATE users SET
                        height = COALESCE($1, height),
                        weight = COALESCE($2, weight),
                        updated_at = NOW()
                    WHERE user_id = $3
                """, height, weight, user_id)
            return user_id

        # Create new user
        user_id = await conn.fetchval("""
            INSERT INTO users (firstname, lastname, birthdate, height, weight)
            VALUES ($1, $2, $3, $4, $5)
            RETURNING user_id
        """, firstname, lastname, birthdate, height, weight)

        logging.info(f"Created new user: {firstname} {lastname} (ID: {user_id})")
        return user_id

    async def get_or_create_heart_rate_device(self, conn, device_name: str) -> int:
        """Get existing heart rate device or create new one, return device_id."""
        if not device_name:
            return None

        device_id = await conn.fetchval("""
            SELECT device_id FROM heart_rate_devices WHERE device_name = $1
        """, device_name)

        if device_id:
            return device_id

        device_id = await conn.fetchval("""
            INSERT INTO heart_rate_devices (device_name)
            VALUES ($1)
            ON CONFLICT (device_name) DO UPDATE SET device_name = EXCLUDED.device_name
            RETURNING device_id
        """, device_name)

        return device_id

    async def get_or_create_session(self, conn, session_id: str, user_id: int,
                                    message_data: Dict[str, Any]) -> None:
        """Create session if it doesn't exist."""
        exists = await conn.fetchval("""
            SELECT 1 FROM tracking_sessions WHERE session_id = $1
        """, session_id)

        if exists:
            return

        # Parse the start_date_time
        start_date_time = None
        if 'startDateTime' in message_data:
            try:
                start_date_time = parser.parse(message_data['startDateTime'])
            except Exception as e:
                logging.warning(f"Could not parse startDateTime: {e}")
                start_date_time = datetime.datetime.now()
        else:
            start_date_time = datetime.datetime.now()

        await conn.execute("""
            INSERT INTO tracking_sessions (
                session_id, user_id, event_name, sport_type, comment, clothing,
                start_date_time, min_distance_meters, min_time_seconds, voice_announcement_interval
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
            ON CONFLICT (session_id) DO NOTHING
        """,
                           session_id, user_id,
                           message_data.get('eventName', ''),
                           message_data.get('sportType', ''),
                           message_data.get('comment', ''),
                           message_data.get('clothing', ''),
                           start_date_time,
                           int(message_data.get('minDistanceMeters', 0)) if message_data.get('minDistanceMeters') else None,
                           int(message_data.get('minTimeSeconds', 0)) if message_data.get('minTimeSeconds') else None,
                           int(message_data.get('voiceAnnouncementInterval', 0)) if message_data.get('voiceAnnouncementInterval') else None
                           )

        logging.info(f"Created session: {session_id} for user: {user_id}")

    async def save_tracking_data_to_db(self, message_data: Dict[str, Any]) -> bool:
        """Save tracking data to normalized PostgreSQL database with weather data in gps_tracking_points."""
        if not self.db_pool:
            logging.error("Database pool not initialized")
            return False

        try:
            async with self.db_pool.acquire() as conn:
                async with conn.transaction():
                    # Get or create user
                    firstname = message_data.get('firstname', message_data.get('person', ''))
                    lastname = message_data.get('lastname', '')
                    birthdate = message_data.get('birthdate', '')
                    height = float(message_data.get('height', 0)) if message_data.get('height') else None
                    weight = float(message_data.get('weight', 0)) if message_data.get('weight') else None

                    user_id = await self.get_or_create_user(
                        conn, firstname, lastname, birthdate, height, weight
                    )

                    # Get or create session
                    session_id = message_data.get('sessionId')
                    await self.get_or_create_session(conn, session_id, user_id, message_data)

                    # Get heart rate device if present
                    heart_rate_device_id = None
                    if message_data.get('heartRateDevice'):
                        heart_rate_device_id = await self.get_or_create_heart_rate_device(
                            conn, message_data.get('heartRateDevice')
                        )

                    # Extract weather data
                    temperature = None
                    wind_speed = None
                    wind_direction = None  # This will be numeric now
                    humidity = None
                    weather_timestamp = None
                    weather_code = None

                    if message_data.get('temperature') is not None:
                        temperature = float(message_data.get('temperature'))
                    if message_data.get('windSpeed') is not None:
                        wind_speed = float(message_data.get('windSpeed'))
                    if message_data.get('windDirection') is not None:
                        # Store as numeric degrees instead of string
                        wind_direction = float(message_data.get('windDirection'))
                    if message_data.get('humidity') is not None:
                        humidity = int(message_data.get('humidity'))
                    if message_data.get('weatherTimestamp') is not None:
                        weather_timestamp = int(message_data.get('weatherTimestamp'))
                    if message_data.get('weatherCode') is not None:
                        weather_code = int(message_data.get('weatherCode'))

                    # Log weather data for debugging
                    if temperature is not None or wind_speed is not None:
                        logging.info(f"Weather data received: temp={temperature}°C, wind={wind_speed}km/h {wind_direction}°, humidity={humidity}%, code={weather_code}")

                    # Insert GPS tracking point with weather data
                    await conn.execute("""
                        INSERT INTO gps_tracking_points (
                            session_id, latitude, longitude, altitude, horizontal_accuracy,
                            vertical_accuracy_meters, number_of_satellites, satellites,
                            used_number_of_satellites, current_speed, average_speed, max_speed,
                            moving_average_speed, speed, speed_accuracy_meters_per_second,
                            distance, covered_distance, cumulative_elevation_gain, heart_rate,
                            heart_rate_device_id, lap, temperature, wind_speed, wind_direction,
                            humidity, weather_timestamp, weather_code
                        ) VALUES (
                            $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15,
                            $16, $17, $18, $19, $20, $21, $22, $23, $24, $25, $26, $27
                        )
                    """,
                                       session_id,
                                       float(message_data.get('latitude', 0)),
                                       float(message_data.get('longitude', 0)),
                                       float(message_data.get('altitude', 0)) if message_data.get('altitude') is not None else None,
                                       float(message_data.get('horizontalAccuracy', 0)) if message_data.get('horizontalAccuracy') is not None else None,
                                       float(message_data.get('verticalAccuracyMeters', 0)) if message_data.get('verticalAccuracyMeters') is not None else None,
                                       int(message_data.get('numberOfSatellites', 0)) if message_data.get('numberOfSatellites') is not None else None,
                                       int(message_data.get('satellites', 0)) if message_data.get('satellites') is not None else None,
                                       int(message_data.get('usedNumberOfSatellites', 0)) if message_data.get('usedNumberOfSatellites') is not None else None,
                                       float(message_data.get('currentSpeed', 0)),
                                       float(message_data.get('averageSpeed', 0)),
                                       float(message_data.get('maxSpeed', 0)),
                                       float(message_data.get('movingAverageSpeed', 0)),
                                       float(message_data.get('speed', 0)) if message_data.get('speed') is not None else float(message_data.get('currentSpeed', 0)),
                                       float(message_data.get('speedAccuracyMetersPerSecond', 0)) if message_data.get('speedAccuracyMetersPerSecond') is not None else None,
                                       float(message_data.get('distance', 0)),
                                       float(message_data.get('coveredDistance', 0)) if message_data.get('coveredDistance') is not None else float(message_data.get('distance', 0)),
                                       float(message_data.get('cumulativeElevationGain', 0)) if message_data.get('cumulativeElevationGain') is not None else None,
                                       int(message_data.get('heartRate', 0)) if message_data.get('heartRate') and message_data.get('heartRate') > 0 else None,
                                       heart_rate_device_id,
                                       int(message_data.get('lap', 0)) if message_data.get('lap') is not None else 0,
                                       temperature,
                                       wind_speed,
                                       wind_direction,
                                       humidity,
                                       weather_timestamp,
                                       weather_code
                                       )

            logging.info(f"Successfully saved normalized tracking data for session {session_id}")
            return True

        except Exception as e:
            logging.error(f"Error saving tracking data to normalized database: {str(e)}")
            logging.error(f"Data that failed to save: {json.dumps(message_data, indent=2)}")
            return False

    async def load_tracking_history_from_db(self) -> None:
        """Load recent tracking history from normalized database using configurable retention period."""
        if not self.db_pool:
            logging.warning("Database pool not initialized, skipping history load")
            return

        try:
            # Use the configurable retention period
            cutoff_time = datetime.datetime.now() - datetime.timedelta(hours=self.data_retention_hours)

            query = """
                SELECT
                    gtp.session_id,
                    u.firstname, u.lastname, u.birthdate, u.height, u.weight,
                    s.event_name, s.sport_type, s.comment, s.clothing,
                    s.min_distance_meters, s.min_time_seconds, s.voice_announcement_interval,
                    gtp.latitude, gtp.longitude, gtp.altitude,
                    gtp.current_speed, gtp.average_speed, gtp.max_speed, gtp.moving_average_speed,
                    gtp.distance, gtp.heart_rate, hrd.device_name as heart_rate_device,
                    gtp.temperature, gtp.wind_speed, gtp.wind_direction,
                    gtp.humidity, gtp.weather_timestamp, gtp.weather_code,
                    gtp.received_at
                FROM gps_tracking_points gtp
                JOIN tracking_sessions s ON gtp.session_id = s.session_id
                JOIN users u ON s.user_id = u.user_id
                LEFT JOIN heart_rate_devices hrd ON gtp.heart_rate_device_id = hrd.device_id
                WHERE gtp.received_at >= $1
                ORDER BY gtp.session_id, gtp.received_at
            """

            async with self.db_pool.acquire() as conn:
                rows = await conn.fetch(query, cutoff_time)

            # Clear existing memory before loading fresh data
            self.tracking_history.clear()

            # Convert database rows to the format expected by the websocket clients
            for row in rows:
                tracking_point = {
                    "timestamp": row['received_at'].strftime(self.timestamp_format),
                    "sessionId": row['session_id'],
                    "firstname": row['firstname'],
                    "lastname": row['lastname'] or '',
                    "birthdate": row['birthdate'] or '',
                    "height": float(row['height']) if row['height'] is not None else 0.0,
                    "weight": float(row['weight']) if row['weight'] is not None else 0.0,
                    "minDistanceMeters": int(row['min_distance_meters']) if row['min_distance_meters'] is not None else 0,
                    "minTimeSeconds": int(row['min_time_seconds']) if row['min_time_seconds'] is not None else 0,
                    "voiceAnnouncementInterval": int(row['voice_announcement_interval']) if row['voice_announcement_interval'] is not None else 0,
                    "eventName": row['event_name'] or '',
                    "sportType": row['sport_type'] or '',
                    "comment": row['comment'] or '',
                    "clothing": row['clothing'] or '',
                    "latitude": float(row['latitude']),
                    "longitude": float(row['longitude']),
                    "altitude": float(row['altitude']) if row['altitude'] is not None else 0.0,
                    "currentSpeed": float(row['current_speed']) if row['current_speed'] else 0.0,
                    "maxSpeed": float(row['max_speed']) if row['max_speed'] else 0.0,
                    "movingAverageSpeed": float(row['moving_average_speed']) if row['moving_average_speed'] else 0.0,
                    "averageSpeed": float(row['average_speed']) if row['average_speed'] else 0.0,
                    "distance": float(row['distance']) if row['distance'] else 0.0,
                    # Keep 'person' for backward compatibility
                    "person": row['firstname']
                }

                # Add heart rate data if available
                if row['heart_rate'] and row['heart_rate'] > 0:
                    tracking_point["heartRate"] = int(row['heart_rate'])
                if row['heart_rate_device']:
                    tracking_point["heartRateDevice"] = row['heart_rate_device']

                # Add weather data if available
                if row['temperature'] is not None:
                    tracking_point["temperature"] = float(row['temperature'])
                if row['wind_speed'] is not None:
                    tracking_point["windSpeed"] = float(row['wind_speed'])
                if row['wind_direction'] is not None:
                    # Handle both numeric and string wind directions for backward compatibility
                    wind_dir = row['wind_direction']
                    if isinstance(wind_dir, (int, float)):
                        tracking_point["windDirection"] = float(wind_dir)
                    else:
                        # Convert string direction to numeric if needed
                        tracking_point["windDirection"] = float(wind_dir) if str(wind_dir).replace('.', '').isdigit() else 0
                if row['humidity'] is not None:
                    tracking_point["relativeHumidity"] = int(row['humidity'])
                if row['weather_timestamp'] is not None:
                    tracking_point["weatherTimestamp"] = int(row['weather_timestamp'])
                if row['weather_code'] is not None:
                    tracking_point["weatherCode"] = int(row['weather_code'])

                self.tracking_history[row['session_id']].append(tracking_point)

            logging.info(f"Loaded {len(rows)} tracking points from database (last {self.data_retention_hours} hours)")

        except Exception as e:
            logging.error(f"Error loading tracking history from normalized database: {str(e)}")

    async def get_weather_data_for_session(self, session_id: str) -> List[Dict[str, Any]]:
        """Retrieve weather data from GPS tracking points for a specific session."""
        if not self.db_pool:
            return []

        try:
            async with self.db_pool.acquire() as conn:
                rows = await conn.fetch("""
                    SELECT 
                        id, latitude, longitude, temperature, wind_speed, wind_direction,
                        humidity, weather_timestamp, weather_code, received_at
                    FROM gps_tracking_points 
                    WHERE session_id = $1 
                    AND (temperature IS NOT NULL OR wind_speed IS NOT NULL OR humidity IS NOT NULL)
                    ORDER BY received_at ASC
                """, session_id)

                weather_data = []
                for row in rows:
                    weather_point = {
                        "id": row['id'],
                        "latitude": float(row['latitude']),
                        "longitude": float(row['longitude']),
                        "temperature": float(row['temperature']) if row['temperature'] is not None else None,
                        "windSpeed": float(row['wind_speed']) if row['wind_speed'] is not None else None,
                        "windDirection": float(row['wind_direction']) if row['wind_direction'] is not None else None,  # Now numeric
                        "humidity": int(row['humidity']) if row['humidity'] is not None else None,
                        "weatherCode": int(row['weather_code']) if row['weather_code'] is not None else None,
                        "weatherTimestamp": int(row['weather_timestamp']) if row['weather_timestamp'] is not None else None,
                        "receivedAt": row['received_at'].isoformat() if row['received_at'] else None
                    }
                    weather_data.append(weather_point)

                return weather_data

        except Exception as e:
            logging.error(f"Error retrieving weather data for session {session_id}: {str(e)}")
            return []

    async def get_weather_summary_for_session(self, session_id: str) -> Dict[str, Any]:
        """Get weather summary statistics for a session."""
        if not self.db_pool:
            return {}

        try:
            async with self.db_pool.acquire() as conn:
                row = await conn.fetchrow("""
                    SELECT 
                        COUNT(*) as weather_point_count,
                        AVG(temperature) as avg_temp,
                        MIN(temperature) as min_temp,
                        MAX(temperature) as max_temp,
                        AVG(wind_speed) as avg_wind_speed,
                        MAX(wind_speed) as max_wind_speed,
                        AVG(humidity) as avg_humidity,
                        MIN(received_at) as first_weather,
                        MAX(received_at) as last_weather
                    FROM gps_tracking_points
                    WHERE session_id = $1
                    AND (temperature IS NOT NULL OR wind_speed IS NOT NULL OR humidity IS NOT NULL)
                """, session_id)

                if row and row['weather_point_count'] > 0:
                    return {
                        "sessionId": session_id,
                        "weatherPointCount": int(row['weather_point_count']),
                        "avgTemperature": float(row['avg_temp']) if row['avg_temp'] is not None else None,
                        "minTemperature": float(row['min_temp']) if row['min_temp'] is not None else None,
                        "maxTemperature": float(row['max_temp']) if row['max_temp'] is not None else None,
                        "avgWindSpeed": float(row['avg_wind_speed']) if row['avg_wind_speed'] is not None else None,
                        "maxWindSpeed": float(row['max_wind_speed']) if row['max_wind_speed'] is not None else None,
                        "avgHumidity": float(row['avg_humidity']) if row['avg_humidity'] is not None else None,
                        "firstWeatherTime": row['first_weather'].isoformat() if row['first_weather'] else None,
                        "lastWeatherTime": row['last_weather'].isoformat() if row['last_weather'] else None
                    }
                else:
                    return {"sessionId": session_id, "weatherPointCount": 0}

        except Exception as e:
            logging.error(f"Error getting weather summary for session {session_id}: {str(e)}")
            return {"sessionId": session_id, "error": str(e)}

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

    async def broadcast_to_followers(self, session_id: str, message: Dict[str, Any]) -> None:
        """Broadcast a message specifically to clients following a particular session."""
        if session_id not in self.session_followers:
            return

        followers = self.session_followers[session_id].copy()
        disconnected_clients = set()

        for client in followers:
            try:
                await client.send(json.dumps(message))
            except websockets.exceptions.ConnectionClosed:
                disconnected_clients.add(client)
            except Exception as e:
                logging.error(f"Error broadcasting to follower: {str(e)}")
                disconnected_clients.add(client)

        # Remove disconnected clients from following
        for client in disconnected_clients:
            self.remove_client_from_following(client)

        if disconnected_clients:
            logging.info(f"Removed {len(disconnected_clients)} disconnected followers")

    def add_following_relationship(self, client: websockets.WebSocketServerProtocol, session_ids: List[str]) -> None:
        """Add following relationships for a client."""
        # Remove client from previous following relationships
        self.remove_client_from_following(client)

        # Add new following relationships
        self.client_following[client] = set(session_ids)

        for session_id in session_ids:
            self.session_followers[session_id].add(client)

        logging.info(f"Client {client.remote_address} now following {len(session_ids)} sessions: {session_ids}")

    def remove_client_from_following(self, client: websockets.WebSocketServerProtocol) -> None:
        """Remove a client from all following relationships."""
        if client in self.client_following:
            followed_sessions = self.client_following[client].copy()

            # Remove from session_followers
            for session_id in followed_sessions:
                if client in self.session_followers[session_id]:
                    self.session_followers[session_id].remove(client)
                    if not self.session_followers[session_id]:
                        del self.session_followers[session_id]

            # Remove from client_following
            del self.client_following[client]

            logging.info(f"Removed client {client.remote_address} from following {len(followed_sessions)} sessions")

    async def broadcast_active_users_update(self) -> None:
        """Broadcast updated active users list to all clients."""
        try:
            self.update_active_sessions()

            # Get current active users with their latest data
            active_users = []
            for session_id in self.active_sessions:
                if session_id in self.tracking_history and self.tracking_history[session_id]:
                    latest_point = self.tracking_history[session_id][-1]
                    active_user = {
                        "sessionId": session_id,
                        "person": latest_point.get("firstname", latest_point.get("person", "")),
                        "eventName": latest_point.get("eventName", ""),
                        "lastUpdate": latest_point.get("timestamp", ""),
                        "latitude": latest_point.get("latitude", 0.0),
                        "longitude": latest_point.get("longitude", 0.0)
                    }
                    active_users.append(active_user)

            # Broadcast to all connected clients
            await self.broadcast_update({
                'type': 'active_users',
                'users': active_users
            })

            logging.info(f"Broadcasted active users update: {len(active_users)} active users")

        except Exception as e:
            logging.error(f"Error broadcasting active users update: {str(e)}")

    async def handle_get_active_users_request(self, websocket: websockets.WebSocketServerProtocol) -> None:
        """Handle request for active users list."""
        try:
            self.update_active_sessions()

            active_users = []
            for session_id in self.active_sessions:
                if session_id in self.tracking_history and self.tracking_history[session_id]:
                    latest_point = self.tracking_history[session_id][-1]
                    active_user = {
                        "sessionId": session_id,
                        "person": latest_point.get("firstname", latest_point.get("person", "")),
                        "eventName": latest_point.get("eventName", ""),
                        "lastUpdate": latest_point.get("timestamp", ""),
                        "latitude": latest_point.get("latitude", 0.0),
                        "longitude": latest_point.get("longitude", 0.0)
                    }
                    active_users.append(active_user)

            # Send response to requesting client
            await websocket.send(json.dumps({
                'type': 'active_users',
                'users': active_users
            }))

            logging.info(f"Sent active users list to client: {len(active_users)} users")

        except Exception as e:
            logging.error(f"Error handling active users request: {str(e)}")

    async def handle_follow_users_request(self, websocket: websockets.WebSocketServerProtocol, session_ids: List[str]) -> None:
        """Handle request to follow specific users."""
        try:
            # Validate that the sessions exist and are active
            valid_session_ids = []
            for session_id in session_ids:
                if session_id in self.active_sessions:
                    valid_session_ids.append(session_id)
                else:
                    logging.warning(f"Client tried to follow inactive/non-existent session: {session_id}")

            if valid_session_ids:
                # Set up following relationship
                self.add_following_relationship(websocket, valid_session_ids)

                # Send latest data for each followed session
                for session_id in valid_session_ids:
                    if session_id in self.tracking_history and self.tracking_history[session_id]:
                        latest_point = self.tracking_history[session_id][-1]

                        # Send followed_user_update message with latest data
                        await websocket.send(json.dumps({
                            'type': 'followed_user_update',
                            'point': {
                                'sessionId': session_id,
                                'person': latest_point.get("firstname", latest_point.get("person", "")),
                                'latitude': latest_point.get("latitude", 0.0),
                                'longitude': latest_point.get("longitude", 0.0),
                                'altitude': latest_point.get("altitude", 0.0),
                                'currentSpeed': latest_point.get("currentSpeed", 0.0),
                                'distance': latest_point.get("distance", 0.0),
                                'heartRate': latest_point.get("heartRate"),
                                'timestamp': latest_point.get("timestamp", "")
                            }
                        }))

            # Send response
            await websocket.send(json.dumps({
                'type': 'follow_response',
                'success': True,
                'following': valid_session_ids
            }))

            logging.info(f"Client {websocket.remote_address} started following {len(valid_session_ids)} sessions")

        except Exception as e:
            logging.error(f"Error handling follow users request: {str(e)}")
            await websocket.send(json.dumps({
                'type': 'follow_response',
                'success': False,
                'error': str(e)
            }))

    async def handle_unfollow_users_request(self, websocket: websockets.WebSocketServerProtocol) -> None:
        """Handle request to stop following all users."""
        try:
            self.remove_client_from_following(websocket)

            await websocket.send(json.dumps({
                'type': 'unfollow_response',
                'success': True
            }))

            logging.info(f"Client {websocket.remote_address} stopped following all users")

        except Exception as e:
            logging.error(f"Error handling unfollow users request: {str(e)}")
            await websocket.send(json.dumps({
                'type': 'unfollow_response',
                'success': False,
                'error': str(e)
            }))

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
            was_active = session_id in self.active_sessions
            # Mark this session as active
            self.active_sessions.add(session_id)
            self.last_activity[session_id] = datetime.datetime.now()

            if not was_active:
                logging.info(f"Session {session_id} became ACTIVE - total active: {len(self.active_sessions)}")

        # Create base tracking point with all fields including weather
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
        elif "person" in message_data and "firstname" not in message_data:
            tracking_point["firstname"] = message_data["person"]

        # Add heart rate data if available
        if "heartRate" in message_data:
            tracking_point["heartRate"] = int(message_data["heartRate"])

        if "heartRateDevice" in message_data:
            tracking_point["heartRateDevice"] = message_data["heartRateDevice"]

        # Add weather data if available (already included in **message_data)
        # Just ensure proper types for weather fields
        if "temperature" in message_data:
            tracking_point["temperature"] = float(message_data["temperature"])

        if "windSpeed" in message_data:
            tracking_point["windSpeed"] = float(message_data["windSpeed"])

        if "windDirection" in message_data:
            tracking_point["windDirection"] = str(message_data["windDirection"])

        if "humidity" in message_data:
            tracking_point["relativeHumidity"] = int(message_data["humidity"])

        if "weatherTimestamp" in message_data:
            tracking_point["weatherTimestamp"] = int(message_data["weatherTimestamp"])

        if "weatherCode" in message_data:
            tracking_point["weatherCode"] = int(message_data["weatherCode"])

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
        """Delete a session and all related data using CASCADE."""
        try:
            # Check if session exists and is not active
            if session_id not in self.tracking_history:
                logging.warning(f"Attempted to delete non-existent session: {session_id}")
                return {"success": False, "reason": "Session does not exist"}

            self.update_active_sessions()
            if session_id in self.active_sessions:
                logging.warning(f"Attempted to delete active session: {session_id}")
                return {"success": False, "reason": "Cannot delete active session"}

            # Delete from memory
            del self.tracking_history[session_id]
            if session_id in self.last_activity:
                del self.last_activity[session_id]

            # Delete from database (CASCADE will handle related GPS points)
            if self.db_pool:
                try:
                    async with self.db_pool.acquire() as conn:
                        await conn.execute("DELETE FROM tracking_sessions WHERE session_id = $1", session_id)
                    logging.info(f"Deleted session from normalized database: {session_id}")
                except Exception as e:
                    logging.error(f"Error deleting session from database: {str(e)}")

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
        last_active_users_broadcast = datetime.datetime.now()
        broadcast_interval = 30  # Broadcast active users every 30 seconds

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

                    # Handle memory cleanup request
                    if message_data.get('type') == 'cleanup_memory':
                        result = await self.manual_cleanup_memory()
                        await websocket.send(json.dumps({
                            'type': 'cleanup_response',
                            'success': result["success"],
                            'message': result["message"]
                        }))
                        continue

                    # Handle get_active_users request
                    if message_data.get('type') == 'get_active_users':
                        await self.handle_get_active_users_request(websocket)
                        continue

                    # Handle follow_users request
                    if message_data.get('type') == 'follow_users':
                        session_ids = message_data.get('sessionIds', [])
                        await self.handle_follow_users_request(websocket, session_ids)
                        continue

                    # Handle unfollow_users request
                    if message_data.get('type') == 'unfollow_users':
                        await self.handle_unfollow_users_request(websocket)
                        continue

                    # Handle get_weather request
                    if message_data.get('type') == 'get_weather':
                        session_id = message_data.get('sessionId')
                        if session_id:
                            try:
                                weather_data = await self.get_weather_data_for_session(session_id)
                                await websocket.send(json.dumps({
                                    'type': 'weather_data',
                                    'sessionId': session_id,
                                    'weather': weather_data
                                }))
                                logging.info(f"Sent weather data for session {session_id}: {len(weather_data)} weather points")
                            except Exception as e:
                                logging.error(f"Error handling weather data request: {str(e)}")
                                await websocket.send(json.dumps({
                                    'type': 'weather_data',
                                    'sessionId': session_id,
                                    'weather': [],
                                    'error': str(e)
                                }))
                        else:
                            await websocket.send(json.dumps({
                                'type': 'weather_data',
                                'error': 'sessionId is required'
                            }))
                        continue

                    # Handle get_weather_summary request
                    if message_data.get('type') == 'get_weather_summary':
                        session_id = message_data.get('sessionId')
                        if session_id:
                            try:
                                summary = await self.get_weather_summary_for_session(session_id)
                                await websocket.send(json.dumps({
                                    'type': 'weather_summary',
                                    'summary': summary
                                }))
                                logging.info(f"Sent weather summary for session {session_id}")
                            except Exception as e:
                                logging.error(f"Error handling weather summary request: {str(e)}")
                                await websocket.send(json.dumps({
                                    'type': 'weather_summary',
                                    'error': str(e)
                                }))
                        else:
                            await websocket.send(json.dumps({
                                'type': 'weather_summary',
                                'error': 'sessionId is required'
                            }))
                        continue

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
                        self.update_active_sessions()
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

                    # Handle tracking data
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

                    # Save to database
                    db_success = await self.save_tracking_data_to_db(message_data)
                    if not db_success:
                        logging.warning("Failed to save to database, but continuing with in-memory storage")

                    # Create and store tracking point
                    old_active_sessions = self.active_sessions.copy()
                    tracking_point = self.create_tracking_point(message_data)
                    session_id = message_data['sessionId']
                    self.tracking_history[session_id].append(tracking_point)

                    # Check if we have new active sessions
                    if session_id not in old_active_sessions:
                        logging.info(f"New active session detected: {session_id}")
                        # Broadcast active users update when new session becomes active
                        await self.broadcast_active_users_update()
                        last_active_users_broadcast = datetime.datetime.now()

                    # Broadcast general tracking update to all clients
                    await self.broadcast_update({
                        'type': 'update',
                        'point': tracking_point
                    })

                    # Send specific followed_user_update to followers of this session
                    if session_id in self.session_followers:
                        follower_update = {
                            'type': 'followed_user_update',
                            'point': {
                                'sessionId': session_id,
                                'person': tracking_point.get("firstname", tracking_point.get("person", "")),
                                'latitude': tracking_point.get("latitude", 0.0),
                                'longitude': tracking_point.get("longitude", 0.0),
                                'altitude': tracking_point.get("altitude", 0.0),
                                'currentSpeed': tracking_point.get("currentSpeed", 0.0),
                                'distance': tracking_point.get("distance", 0.0),
                                'heartRate': tracking_point.get("heartRate"),
                                'timestamp': tracking_point.get("timestamp", "")
                            }
                        }

                        await self.broadcast_to_followers(session_id, follower_update)
                        logging.info(f"Sent followed_user_update for session {session_id} to {len(self.session_followers[session_id])} followers")

                    # Periodically broadcast active users list
                    now = datetime.datetime.now()
                    if (now - last_active_users_broadcast).total_seconds() > broadcast_interval:
                        await self.broadcast_active_users_update()
                        last_active_users_broadcast = now

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
                # Clean up following relationships for this client
                self.remove_client_from_following(websocket)
                logging.info(f"Removed client {client_address} from connected_clients and following relationships")

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

    # Start the periodic cleanup task if enabled
    cleanup_task = None
    if server.enable_automatic_cleanup:
        cleanup_task = asyncio.create_task(server.periodic_cleanup_task())
        logging.info("Automatic memory cleanup task started")
    else:
        logging.info("Automatic memory cleanup is disabled")

    try:
        async with websockets.serve(server.handle_client, "0.0.0.0", 6789):
            try:
                await asyncio.Future()  # run forever
            except asyncio.CancelledError:
                pass
    finally:
        # Cancel cleanup task if it was started
        if cleanup_task and not cleanup_task.done():
            cleanup_task.cancel()
            try:
                await cleanup_task
            except asyncio.CancelledError:
                logging.info("Cleanup task cancelled during shutdown")

        # Clean up database connections if they exist
        try:
            await server.close_database()
        except:
            pass

if __name__ == "__main__":
    asyncio.run(main())