#!/usr/bin/env python3
"""
Enhanced GPS tracking data exporter from PostgreSQL to GPX format

Features:
- Export data from any date (not just today)
- Comprehensive sensor data including:
  * Heart rate monitoring
  * Barometer altitude and pressure readings
  * GPS signal quality (satellites, accuracy)
  * Weather conditions (temperature, wind, humidity)
  * Lap information and timing
  * Speed and elevation data
- Support for waypoints and route planning
- Detailed metadata and extensions in GPX format

How to use:
- search and change username and password
"""

import asyncio
import asyncpg
import datetime
import xml.etree.ElementTree as ET
from xml.dom import minidom
import sys
import os
from typing import List, Dict, Optional

class GPXExporter:
    def __init__(self, db_host: str = "localhost", db_port: int = 8021,
                 db_name: str = "geotracker", db_user: str = "username",
                 db_password: str = "password"):
        self.db_config = {
            'host': db_host,
            'port': db_port,
            'database': db_name,
            'user': db_user,
            'password': db_password
        }

    async def get_sessions_by_date(self, target_date: datetime.date = None) -> List[Dict]:
        """Get all tracking sessions from specified date (defaults to today)"""
        if target_date is None:
            target_date = datetime.date.today()

        conn = await asyncpg.connect(**self.db_config)
        try:
            # Get sessions that were created on the specified date
            sessions = await conn.fetch("""
                SELECT
                    s.session_id,
                    s.event_name,
                    s.sport_type,
                    s.comment,
                    s.start_date_time,
                    u.firstname,
                    u.lastname,
                    COUNT(gtp.id) as point_count
                FROM tracking_sessions s
                JOIN users u ON s.user_id = u.user_id
                LEFT JOIN gps_tracking_points gtp ON s.session_id = gtp.session_id
                WHERE DATE(s.created_at) = $1
                GROUP BY s.session_id, s.event_name, s.sport_type, s.comment,
                         s.start_date_time, u.firstname, u.lastname
                ORDER BY s.start_date_time DESC
            """, target_date)

            return [dict(session) for session in sessions]
        finally:
            await conn.close()

    async def get_todays_sessions(self) -> List[Dict]:
        """Get all tracking sessions from today (kept for backward compatibility)"""
        return await self.get_sessions_by_date()

    async def get_session_gps_data(self, session_id: str) -> List[Dict]:
        """Get all GPS tracking points for a session with comprehensive data"""
        conn = await asyncpg.connect(**self.db_config)
        try:
            points = await conn.fetch("""
                SELECT
                    latitude,
                    longitude,
                    altitude,
                    current_speed,
                    average_speed,
                    max_speed,
                    moving_average_speed,
                    speed,
                    speed_accuracy_meters_per_second,
                    heart_rate,
                    distance,
                    covered_distance,
                    cumulative_elevation_gain,
                    lap,

                    -- Weather data
                    temperature,
                    wind_speed,
                    wind_direction,
                    humidity,
                    weather_code,
                    weather_timestamp,

                    -- Barometer data
                    pressure,
                    pressure_accuracy,
                    altitude_from_pressure,
                    sea_level_pressure,

                    -- GPS signal quality
                    horizontal_accuracy,
                    vertical_accuracy_meters,
                    number_of_satellites,
                    used_number_of_satellites,

                    received_at,
                    created_at
                FROM gps_tracking_points
                WHERE session_id = $1
                ORDER BY received_at ASC
            """, session_id)

            return [dict(point) for point in points]
        finally:
            await conn.close()

    async def get_session_waypoints(self, session_id: str) -> List[Dict]:
        """Get all waypoints for a session"""
        conn = await asyncpg.connect(**self.db_config)
        try:
            waypoints = await conn.fetch("""
                SELECT
                    waypoint_name,
                    waypoint_description,
                    latitude,
                    longitude,
                    elevation,
                    waypoint_timestamp,
                    received_at
                FROM waypoints
                WHERE session_id = $1
                ORDER BY waypoint_timestamp ASC
            """, session_id)

            return [dict(waypoint) for waypoint in waypoints]
        finally:
            await conn.close()

    async def get_session_lap_data(self, session_id: str) -> List[Dict]:
        """Get lap information for a session"""
        conn = await asyncpg.connect(**self.db_config)
        try:
            # Check if lap_times table exists and get lap data
            laps = await conn.fetch("""
                SELECT DISTINCT
                    lap,
                    MIN(received_at) as lap_start_time,
                    MAX(received_at) as lap_end_time,
                    COUNT(*) as point_count,
                    MAX(distance) - MIN(distance) as lap_distance,
                    AVG(current_speed) as avg_speed,
                    MAX(current_speed) as max_speed,
                    MAX(cumulative_elevation_gain) - MIN(cumulative_elevation_gain) as elevation_gain
                FROM gps_tracking_points
                WHERE session_id = $1 AND lap IS NOT NULL
                GROUP BY lap
                ORDER BY lap ASC
            """, session_id)

            return [dict(lap) for lap in laps]
        except Exception:
            # If lap data is not available, return empty list
            return []
        finally:
            await conn.close()

    def create_gpx_element(self) -> ET.Element:
        """Create the root GPX element with proper namespace"""
        gpx = ET.Element("gpx")
        gpx.set("version", "1.1")
        gpx.set("creator", "GeoTracker PostgreSQL Exporter")
        gpx.set("xmlns", "http://www.topografix.com/GPX/1/1")
        gpx.set("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
        gpx.set("xsi:schemaLocation",
                "http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd")
        return gpx

    def add_metadata(self, gpx: ET.Element, session: Dict, lap_data: List[Dict] = None) -> None:
        """Add comprehensive metadata to GPX including lap information"""
        metadata = ET.SubElement(gpx, "metadata")

        name = ET.SubElement(metadata, "name")
        name.text = f"{session['event_name']} - {session['firstname']} {session['lastname']}"

        desc = ET.SubElement(metadata, "desc")
        desc_text = f"Sport: {session['sport_type']}"
        if session['comment']:
            desc_text += f" | Comment: {session['comment']}"
        desc_text += f" | Points: {session['point_count']}"

        if lap_data and len(lap_data) > 0:
            desc_text += f" | Laps: {len(lap_data)}"

        desc.text = desc_text

        if session['start_date_time']:
            time_elem = ET.SubElement(metadata, "time")
            time_elem.text = session['start_date_time'].isoformat()

        # Add lap information as metadata extensions
        if lap_data and len(lap_data) > 0:
            extensions = ET.SubElement(metadata, "extensions")
            laps_elem = ET.SubElement(extensions, "laps")

            for lap in lap_data:
                lap_elem = ET.SubElement(laps_elem, "lap")
                lap_elem.set("number", str(lap['lap']))

                if lap['lap_start_time']:
                    start_time = ET.SubElement(lap_elem, "start_time")
                    start_time.text = lap['lap_start_time'].isoformat()

                if lap['lap_end_time']:
                    end_time = ET.SubElement(lap_elem, "end_time")
                    end_time.text = lap['lap_end_time'].isoformat()

                if lap['lap_distance'] is not None:
                    distance = ET.SubElement(lap_elem, "distance")
                    distance.text = str(lap['lap_distance'])

                if lap['avg_speed'] is not None:
                    avg_speed = ET.SubElement(lap_elem, "avg_speed")
                    avg_speed.text = str(lap['avg_speed'])

                if lap['max_speed'] is not None:
                    max_speed = ET.SubElement(lap_elem, "max_speed")
                    max_speed.text = str(lap['max_speed'])

                if lap['elevation_gain'] is not None:
                    elev_gain = ET.SubElement(lap_elem, "elevation_gain")
                    elev_gain.text = str(lap['elevation_gain'])

                point_count = ET.SubElement(lap_elem, "point_count")
                point_count.text = str(lap['point_count'])

    def add_waypoints(self, gpx: ET.Element, waypoints: List[Dict]) -> None:
        """Add waypoints to GPX"""
        for waypoint in waypoints:
            wpt = ET.SubElement(gpx, "wpt")
            wpt.set("lat", str(waypoint['latitude']))
            wpt.set("lon", str(waypoint['longitude']))

            if waypoint['elevation'] is not None:
                ele = ET.SubElement(wpt, "ele")
                ele.text = str(waypoint['elevation'])

            if waypoint['received_at']:
                time_elem = ET.SubElement(wpt, "time")
                time_elem.text = waypoint['received_at'].isoformat()

            name = ET.SubElement(wpt, "name")
            name.text = waypoint['waypoint_name']

            if waypoint['waypoint_description']:
                desc = ET.SubElement(wpt, "desc")
                desc.text = waypoint['waypoint_description']

    def add_track(self, gpx: ET.Element, session: Dict, gps_points: List[Dict]) -> None:
        """Add track data to GPX"""
        if not gps_points:
            return

        trk = ET.SubElement(gpx, "trk")

        name = ET.SubElement(trk, "name")
        name.text = f"{session['event_name']} Track"

        desc = ET.SubElement(trk, "desc")
        desc.text = f"GPS track for {session['sport_type']} activity"

        trkseg = ET.SubElement(trk, "trkseg")

        for point in gps_points:
            trkpt = ET.SubElement(trkseg, "trkpt")
            trkpt.set("lat", str(point['latitude']))
            trkpt.set("lon", str(point['longitude']))

            if point['altitude'] is not None:
                ele = ET.SubElement(trkpt, "ele")
                ele.text = str(point['altitude'])

            if point['received_at']:
                time_elem = ET.SubElement(trkpt, "time")
                time_elem.text = point['received_at'].isoformat()

            # Add extensions for comprehensive additional data
            extensions_needed = any([
                point['current_speed'], point['heart_rate'], point['temperature'],
                point['pressure'], point['distance'], point['wind_speed'],
                point['altitude_from_pressure'], point['number_of_satellites'],
                point['lap'], point['pressure_accuracy'], point['horizontal_accuracy']
            ])

            if extensions_needed:
                extensions = ET.SubElement(trkpt, "extensions")

                # Speed data
                if point['current_speed'] is not None:
                    speed = ET.SubElement(extensions, "speed")
                    speed.text = str(point['current_speed'])

                if point['average_speed'] is not None:
                    avg_speed = ET.SubElement(extensions, "avg_speed")
                    avg_speed.text = str(point['average_speed'])

                if point['max_speed'] is not None:
                    max_speed = ET.SubElement(extensions, "max_speed")
                    max_speed.text = str(point['max_speed'])

                if point['speed_accuracy_meters_per_second'] is not None:
                    speed_acc = ET.SubElement(extensions, "speed_accuracy")
                    speed_acc.text = str(point['speed_accuracy_meters_per_second'])

                # Heart rate data
                if point['heart_rate'] is not None:
                    hr = ET.SubElement(extensions, "hr")
                    hr.text = str(int(point['heart_rate']))

                # Weather data
                if point['temperature'] is not None:
                    temp = ET.SubElement(extensions, "temp")
                    temp.text = str(point['temperature'])

                if point['wind_speed'] is not None:
                    wind_speed = ET.SubElement(extensions, "wind_speed")
                    wind_speed.text = str(point['wind_speed'])

                if point['wind_direction'] is not None:
                    wind_dir = ET.SubElement(extensions, "wind_direction")
                    wind_dir.text = str(point['wind_direction'])

                if point['humidity'] is not None:
                    humidity = ET.SubElement(extensions, "humidity")
                    humidity.text = str(point['humidity'])

                if point['weather_code'] is not None:
                    weather = ET.SubElement(extensions, "weather_code")
                    weather.text = str(point['weather_code'])

                # Barometer data
                if point['pressure'] is not None:
                    press = ET.SubElement(extensions, "pressure")
                    press.text = str(point['pressure'])

                if point['altitude_from_pressure'] is not None:
                    baro_alt = ET.SubElement(extensions, "barometer_altitude")
                    baro_alt.text = str(point['altitude_from_pressure'])

                if point['sea_level_pressure'] is not None:
                    sea_press = ET.SubElement(extensions, "sea_level_pressure")
                    sea_press.text = str(point['sea_level_pressure'])

                if point['pressure_accuracy'] is not None:
                    press_acc = ET.SubElement(extensions, "pressure_accuracy")
                    press_acc.text = str(point['pressure_accuracy'])

                # GPS signal quality
                if point['horizontal_accuracy'] is not None:
                    h_acc = ET.SubElement(extensions, "horizontal_accuracy")
                    h_acc.text = str(point['horizontal_accuracy'])

                if point['vertical_accuracy_meters'] is not None:
                    v_acc = ET.SubElement(extensions, "vertical_accuracy")
                    v_acc.text = str(point['vertical_accuracy_meters'])

                if point['number_of_satellites'] is not None:
                    sats = ET.SubElement(extensions, "satellites")
                    sats.text = str(point['number_of_satellites'])

                if point['used_number_of_satellites'] is not None:
                    used_sats = ET.SubElement(extensions, "used_satellites")
                    used_sats.text = str(point['used_number_of_satellites'])

                # Distance and elevation data
                if point['distance'] is not None:
                    dist = ET.SubElement(extensions, "distance")
                    dist.text = str(point['distance'])

                if point['covered_distance'] is not None:
                    covered = ET.SubElement(extensions, "covered_distance")
                    covered.text = str(point['covered_distance'])

                if point['cumulative_elevation_gain'] is not None:
                    elev_gain = ET.SubElement(extensions, "elevation_gain")
                    elev_gain.text = str(point['cumulative_elevation_gain'])

                # Lap information
                if point['lap'] is not None:
                    lap = ET.SubElement(extensions, "lap")
                    lap.text = str(point['lap'])

    def format_xml(self, element: ET.Element) -> str:
        """Format XML with proper indentation"""
        rough_string = ET.tostring(element, encoding='unicode')
        reparsed = minidom.parseString(rough_string)
        return reparsed.toprettyxml(indent="  ")

    async def export_session_to_gpx(self, session_id: str, output_file: str, target_date: datetime.date = None) -> bool:
        """Export a single session to GPX file with comprehensive data"""
        try:
            # Get session data
            sessions = await self.get_sessions_by_date(target_date)
            session = next((s for s in sessions if s['session_id'] == session_id), None)

            if not session:
                date_str = target_date.strftime("%Y-%m-%d") if target_date else "today"
                print(f"Session {session_id} not found in {date_str}'s sessions")
                return False

            # Get GPS points, waypoints, and lap data
            gps_points = await self.get_session_gps_data(session_id)
            waypoints = await self.get_session_waypoints(session_id)
            lap_data = await self.get_session_lap_data(session_id)

            if not gps_points and not waypoints:
                print(f"No GPS data or waypoints found for session {session_id}")
                return False

            # Create GPX with comprehensive metadata
            gpx = self.create_gpx_element()
            self.add_metadata(gpx, session, lap_data)

            if waypoints:
                self.add_waypoints(gpx, waypoints)

            if gps_points:
                self.add_track(gpx, session, gps_points)

            # Write to file
            gpx_content = self.format_xml(gpx)
            with open(output_file, 'w', encoding='utf-8') as f:
                f.write(gpx_content)

            print(f"Enhanced GPX file created: {output_file}")
            print(f"  Event: {session['event_name']}")
            print(f"  Sport: {session['sport_type']}")
            print(f"  GPS Points: {len(gps_points)}")
            print(f"  Waypoints: {len(waypoints)}")
            print(f"  Laps: {len(lap_data)}")

            # Print data availability summary
            if gps_points:
                sample_point = gps_points[0]
                available_data = []
                if sample_point.get('heart_rate'): available_data.append("Heart Rate")
                if sample_point.get('pressure'): available_data.append("Barometer")
                if sample_point.get('temperature'): available_data.append("Weather")
                if sample_point.get('number_of_satellites'): available_data.append("GPS Quality")
                if available_data:
                    print(f"  Additional data: {', '.join(available_data)}")

            return True

        except Exception as e:
            print(f"Error exporting session {session_id}: {e}")
            return False

    async def list_sessions_by_date(self, target_date: datetime.date = None):
        """List all sessions from specified date (defaults to today)"""
        try:
            sessions = await self.get_sessions_by_date(target_date)
            date_str = target_date.strftime("%Y-%m-%d") if target_date else "today"

            if not sessions:
                print(f"No sessions found for {date_str}")
                return

            print(f"Found {len(sessions)} session(s) for {date_str}:")
            print("-" * 80)

            for i, session in enumerate(sessions, 1):
                print(f"{i}. Session ID: {session['session_id']}")
                print(f"   Event: {session['event_name']}")
                print(f"   Sport: {session['sport_type']}")
                print(f"   User: {session['firstname']} {session['lastname']}")
                print(f"   Start: {session['start_date_time']}")
                print(f"   GPS Points: {session['point_count']}")
                if session['comment']:
                    print(f"   Comment: {session['comment']}")
                print()

        except Exception as e:
            print(f"Error listing sessions: {e}")

    async def list_todays_sessions(self):
        """List all sessions from today (kept for backward compatibility)"""
        await self.list_sessions_by_date()

def parse_date(date_str: str) -> datetime.date:
    """Parse date string in YYYY-MM-DD format"""
    try:
        return datetime.datetime.strptime(date_str, "%Y-%m-%d").date()
    except ValueError:
        raise ValueError(f"Invalid date format '{date_str}'. Use YYYY-MM-DD format (e.g., 2024-03-15)")

async def main():
    """Main function"""
    if len(sys.argv) < 2:
        print("Usage:")
        print("  python export_todays_gpx.py list [YYYY-MM-DD]                    # List sessions for date (default: today)")
        print("  python export_todays_gpx.py export <session_id> [YYYY-MM-DD]     # Export specific session from date")
        print("  python export_todays_gpx.py export-all [YYYY-MM-DD]              # Export all sessions from date")
        print("")
        print("Examples:")
        print("  python export_todays_gpx.py list                                 # List today's sessions")
        print("  python export_todays_gpx.py list 2024-03-15                      # List sessions from March 15, 2024")
        print("  python export_todays_gpx.py export abc123 2024-03-15             # Export session abc123 from March 15")
        print("  python export_todays_gpx.py export-all 2024-03-15                # Export all sessions from March 15")
        return

    # Database configuration - modify these values for your setup
    exporter = GPXExporter(
        db_host="localhost",
        db_port=8021,
        db_name="geotracker",  # Change this to your database name
        db_user="username",     # Change this to your username
        db_password="password"  # Change this to your password
    )

    command = sys.argv[1].lower()

    try:
        if command == "list":
            target_date = None
            if len(sys.argv) >= 3:
                target_date = parse_date(sys.argv[2])
            await exporter.list_sessions_by_date(target_date)

        elif command == "export" and len(sys.argv) >= 3:
            session_id = sys.argv[2]
            target_date = None

            if len(sys.argv) >= 4:
                target_date = parse_date(sys.argv[3])

            # Use target date for filename, or today if not specified
            date_for_filename = target_date if target_date else datetime.date.today()
            date_str = date_for_filename.strftime("%Y%m%d")
            output_file = f"geotracker_{session_id}_{date_str}.gpx"

            await exporter.export_session_to_gpx(session_id, output_file, target_date)

        elif command == "export-all":
            target_date = None
            if len(sys.argv) >= 3:
                target_date = parse_date(sys.argv[2])

            sessions = await exporter.get_sessions_by_date(target_date)

            # Use target date for filename, or today if not specified
            date_for_filename = target_date if target_date else datetime.date.today()
            date_str = date_for_filename.strftime("%Y%m%d")

            if not sessions:
                date_display = target_date.strftime("%Y-%m-%d") if target_date else "today"
                print(f"No sessions found for {date_display}")
                return

            for session in sessions:
                session_id = session['session_id']
                event_name = session['event_name'].replace(' ', '_').replace('/', '_')
                output_file = f"geotracker_{event_name}_{session_id}_{date_str}.gpx"
                await exporter.export_session_to_gpx(session_id, output_file, target_date)

        else:
            print("Invalid command. Use 'list', 'export <session_id>', or 'export-all'")
            print("Run without arguments to see usage examples.")

    except ValueError as e:
        print(f"Error: {e}")
    except Exception as e:
        print(f"Unexpected error: {e}")

if __name__ == "__main__":
    asyncio.run(main())
