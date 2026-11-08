"""Exercise the media endpoint as a seeking player, without a database server."""
import importlib.util
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from flask import Flask


# Load only this blueprint; unrelated API endpoints require additional services.
spec = importlib.util.spec_from_file_location(
    'app.api.media_playback_under_test',
    Path(__file__).resolve().parents[1] / 'app/api/media.py')
media = importlib.util.module_from_spec(spec)
spec.loader.exec_module(media)


class MediaPlaybackTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.content = bytes(range(256)) * 4096
        path = Path(directory.name) / 'video.mp4'
        path.write_bytes(self.content)
        model = Mock()
        model.query.filter_by.return_value.first.return_value = SimpleNamespace(
            file_extension='mp4', original_filename='video.mp4')
        for target, value in (('SessionMedia', model),
                              ('get_file_paths', Mock(return_value=(str(path), 'unused')))):
            patcher = patch.object(media, target, value)
            patcher.start()
            self.addCleanup(patcher.stop)
        app = Flask(__name__)
        app.config['TESTING'] = True
        app.register_blueprint(media.media_bp, url_prefix='/api')
        self.client = app.test_client()

    def test_full_download_and_head(self):
        with self.client.get('/api/media/video') as response:
            self.assertEqual(200, response.status_code)
            self.assertEqual(self.content, response.data)
            self.assertEqual('video/mp4', response.mimetype)
            self.assertEqual('bytes', response.headers['Accept-Ranges'])
        with self.client.head('/api/media/video') as response:
            self.assertEqual(200, response.status_code)
            self.assertEqual(len(self.content), response.content_length)
            self.assertEqual(b'', response.data)

    def test_player_can_read_start_tail_and_seek(self):
        size = len(self.content)
        for header, start, stop in (
            ('bytes=0-1023', 0, 1024),
            ('bytes=-2048', size - 2048, size),
            ('bytes=524288-', 524288, size),
            (f'bytes={size - 256}-{size + 100}', size - 256, size),
        ):
            with self.subTest(range=header), self.client.get(
                    '/api/media/video', headers={'Range': header}) as response:
                self.assertEqual(206, response.status_code)
                self.assertEqual(self.content[start:stop], response.data)
                self.assertEqual(stop - start, response.content_length)
                self.assertEqual(f'bytes {start}-{stop - 1}/{size}',
                                 response.headers['Content-Range'])

    def test_invalid_ranges_are_rejected(self):
        size = len(self.content)
        for header in (f'bytes={size}-', 'bytes=100-10', 'bytes=broken'):
            with self.subTest(range=header), self.client.get(
                    '/api/media/video', headers={'Range': header}) as response:
                self.assertEqual(416, response.status_code)
                self.assertEqual(f'bytes */{size}', response.headers.get('Content-Range'))

    def test_if_range_does_not_mix_changed_file_with_old_bytes(self):
        with self.client.get('/api/media/video', headers={
            'Range': 'bytes=100-199', 'If-Range': '"outdated-file"'
        }) as response:
            self.assertEqual(200, response.status_code)
            self.assertEqual(self.content, response.data)


if __name__ == '__main__':
    unittest.main()
