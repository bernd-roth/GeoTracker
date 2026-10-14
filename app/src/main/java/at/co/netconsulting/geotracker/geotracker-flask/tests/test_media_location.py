import importlib.util
import json
import shutil
import subprocess
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    'media_location', Path(__file__).resolve().parents[1] / 'app/utils/media_location.py')
location = importlib.util.module_from_spec(spec)
spec.loader.exec_module(location)


class MediaLocationTest(unittest.TestCase):
    def test_video_iso6709(self):
        self.assertEqual((48.2082, 16.3738), location.parse_video_location('+48.2082+016.3738/'))
        self.assertEqual((-33.86, -151.2), location.parse_video_location('-33.86-151.20+15.5/'))
        self.assertEqual((0.0, 0.0), location.parse_video_location('+00.0+000.0/'))
        for invalid in (None, '', '48,16', '+91.0+016.0/', '+48.0+181.0/', '+48.0+016.0/junk'):
            self.assertIsNone(location.parse_video_location(invalid))

    def test_missing_invalid_coordinates(self):
        for latitude, longitude in ((None, 16), (48, None), ('NaN', 16), (48, 'inf'), (91, 0)):
            self.assertIsNone(location.valid_coordinates(latitude, longitude))

    def test_real_photo_exif_and_untagged_photo(self):
        from PIL import Image
        from PIL.TiffImagePlugin import IFDRational
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'photo.jpg'
            image = Image.new('RGB', (8, 8))
            exif = Image.Exif()
            exif[0x8825] = {1: 'S', 2: tuple(map(IFDRational, (33, 30, 0))),
                            3: 'W', 4: tuple(map(IFDRational, (151, 15, 0)))}
            image.save(path, exif=exif)
            self.assertEqual((-33.5, -151.25), location.extract_media_location(path, 'image'))
            image.save(path)
            self.assertIsNone(location.extract_media_location(path, 'image'))
            path.write_bytes(b'broken image')
            self.assertIsNone(location.extract_media_location(path, 'image'))

    def test_quicktime_and_stream_video_tags(self):
        for metadata in (
            {'format': {'tags': {'com.apple.quicktime.location.ISO6709': '+48.2+016.3/'}}},
            {'streams': [{'tags': {'location': '+48.2+016.3/'}}]},
        ):
            with patch.object(location.subprocess, 'run') as probe:
                probe.return_value.stdout = json.dumps(metadata)
                self.assertEqual((48.2, 16.3), location.extract_media_location('video.mov', 'video'))
        with patch.object(location.subprocess, 'run', side_effect=FileNotFoundError):
            self.assertIsNone(location.extract_media_location('video.mov', 'video'))

    @unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'FFmpeg tools unavailable')
    def test_real_tagged_video(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'video.mov'
            subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i',
                            'color=c=black:s=16x16:d=0.1', '-c:v', 'mpeg4',
                            '-metadata', 'location=+48.2082+016.3738/', str(path)],
                           check=True, capture_output=True, timeout=15)
            self.assertEqual((48.2082, 16.3738), location.extract_media_location(path, 'video'))


if __name__ == '__main__':
    unittest.main()
