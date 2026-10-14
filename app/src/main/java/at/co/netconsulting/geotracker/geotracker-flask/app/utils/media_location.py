"""Read optional GPS metadata without preventing uploads of unsupported media."""
import json
import math
import re
import subprocess


def valid_coordinates(latitude, longitude):
    try:
        latitude, longitude = float(latitude), float(longitude)
        if (math.isfinite(latitude) and math.isfinite(longitude)
                and -90 <= latitude <= 90 and -180 <= longitude <= 180):
            return latitude, longitude
    except (TypeError, ValueError, OverflowError):
        pass
    return None


def parse_video_location(value):
    match = re.fullmatch(
        r'([+-]\d{2}(?:\.\d+)?)([+-]\d{3}(?:\.\d+)?)(?:[+-]\d+(?:\.\d+)?)?/?',
        str(value).strip(),
    )
    return valid_coordinates(*match.groups()) if match else None


def extract_media_location(path, media_type):
    try:
        if media_type == 'image':
            from PIL import Image
            if str(path).lower().endswith(('.heic', '.heif')):
                from pillow_heif import register_heif_opener
                register_heif_opener()
            with Image.open(path) as image:
                gps = image.getexif().get_ifd(0x8825)
                if not gps or gps.get(1) not in ('N', 'S') or gps.get(3) not in ('E', 'W'):
                    return None

                def degrees(values, reference):
                    d, m, s = map(float, values)
                    if not (d >= 0 and 0 <= m < 60 and 0 <= s < 60):
                        raise ValueError('Invalid GPS degrees')
                    return (d + m / 60 + s / 3600) * (-1 if reference in ('S', 'W') else 1)

                return valid_coordinates(degrees(gps[2], gps[1]), degrees(gps[4], gps[3]))
        if media_type == 'video':
            result = subprocess.run(
                ['ffprobe', '-v', 'error', '-show_entries', 'format_tags:stream_tags', '-of', 'json', str(path)],
                capture_output=True, text=True, timeout=15, check=True,
            )
            metadata = json.loads(result.stdout)
            containers = [metadata.get('format', {})] + metadata.get('streams', [])
            for container in containers:
                for key, value in container.get('tags', {}).items():
                    if key.lower() in ('location', 'location-eng', 'com.apple.quicktime.location.iso6709'):
                        location = parse_video_location(value)
                        if location is not None:
                            return location
    except Exception:
        # Missing, malformed, or unsupported metadata is normal for attachments.
        pass
    return None
