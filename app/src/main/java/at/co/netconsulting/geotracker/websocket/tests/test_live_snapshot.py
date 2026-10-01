import importlib.util
import json
import logging
import logging.handlers
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import AsyncMock, MagicMock


def load_websocket_server_module():
    websockets = types.ModuleType("websockets")
    websockets.WebSocketServerProtocol = type("WebSocketServerProtocol", (), {})
    websockets.exceptions = types.SimpleNamespace(ConnectionClosed=Exception)
    websockets.serve = None

    asyncpg = types.ModuleType("asyncpg")
    asyncpg.Pool = type("Pool", (), {})
    asyncpg.create_pool = None

    redis_package = types.ModuleType("redis")
    redis_asyncio = types.ModuleType("redis.asyncio")
    redis_asyncio.Redis = type("Redis", (), {})
    redis_package.asyncio = redis_asyncio

    dateutil = types.ModuleType("dateutil")
    dateutil.parser = types.SimpleNamespace()

    stub_modules = {
        "websockets": websockets,
        "asyncpg": asyncpg,
        "redis": redis_package,
        "redis.asyncio": redis_asyncio,
        "dateutil": dateutil,
    }
    previous_modules = {name: sys.modules.get(name) for name in stub_modules}
    sys.modules.update(stub_modules)

    class StubRotatingFileHandler(logging.NullHandler):
        def __init__(self, *args, **kwargs):
            super().__init__()

    original_handler = logging.handlers.RotatingFileHandler
    logging.handlers.RotatingFileHandler = StubRotatingFileHandler
    try:
        module_path = Path(__file__).resolve().parents[1] / "websocket_server.py"
        spec = importlib.util.spec_from_file_location("geotracker_websocket_server", module_path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
    finally:
        logging.handlers.RotatingFileHandler = original_handler
        for name, previous in previous_modules.items():
            if previous is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = previous


websocket_server = load_websocket_server_module()


class LiveSnapshotTest(unittest.TestCase):
    def setUp(self):
        self.server = websocket_server.TrackingServer()

    def test_empty_redis_snapshot_does_not_expose_stale_memory_sessions(self):
        self.server.tracking_history["stale_session"].append({
            "sessionId": "stale_session",
            "person": "Bernd",
        })

        self.assertEqual([], self.server.build_session_info([]))

    def test_session_metadata_uses_latest_point_from_the_graph_snapshot(self):
        self.server.active_sessions.add("retained_session")
        points = [
            {
                "sessionId": "retained_session",
                "person": "Bernd",
                "sportType": "Walking",
            },
            {
                "sessionId": "retained_session",
                "person": "Bernd",
                "sportType": "Running",
            },
        ]

        self.assertEqual([{
            "sessionId": "retained_session",
            "isActive": True,
            "person": "Bernd",
            "eventName": "",
            "sportType": "Running",
            "startDateTime": None,
            "startCity": "",
            "startCountry": "",
            "startAddress": "",
            "endCity": "",
            "endCountry": "",
            "endAddress": "",
            "version": "",
        }], self.server.build_session_info(points))

    def test_zero_pressure_is_removed_from_tracking_points(self):
        point = {
            "pressure": 0,
            "pressureAccuracy": 0,
            "altitudeFromPressure": 0,
            "seaLevelPressure": 1013.25,
            "sessionId": "pressure-session",
        }

        normalized = self.server.normalize_tracking_point_pressure(point)

        self.assertNotIn("pressure", normalized)
        self.assertNotIn("pressureAccuracy", normalized)
        self.assertNotIn("altitudeFromPressure", normalized)
        self.assertNotIn("seaLevelPressure", normalized)

    def test_real_pressure_is_preserved_as_a_number(self):
        point = {"pressure": "978.57", "sessionId": "pressure-session"}

        normalized = self.server.normalize_tracking_point_pressure(point)

        self.assertEqual(978.57, normalized["pressure"])


class SessionNamesTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.server = websocket_server.TrackingServer()
        self.connection = AsyncMock()
        self.server.db_pool = MagicMock()
        self.server.db_pool.acquire.return_value.__aenter__.return_value = self.connection
        self.server.redis_client = AsyncMock()
        self.server.broadcast_update = AsyncMock()

    async def test_saved_name_overrides_cached_name_by_id_without_changing_points(self):
        self.connection.fetch.return_value = [
            {'session_id': 'retained', 'event_name': 'New name'},
        ]
        points = [{'sessionId': 'retained', 'eventName': 'Old name'}]
        sessions = self.server.build_session_info(points)
        await self.server.apply_saved_event_names(sessions)

        self.assertEqual('New name', sessions[0]['eventName'])
        self.assertEqual('New name', sessions[0]['savedEventName'])
        self.assertEqual('Old name', points[0]['eventName'])
        self.assertEqual([], self.server.redis_client.mock_calls)
        self.assertEqual(['retained'], self.connection.fetch.await_args.args[1])

    async def test_empty_snapshot_does_not_query_or_restore_database_sessions(self):
        sessions = self.server.build_session_info([])
        await self.server.apply_saved_event_names(sessions)
        self.assertEqual([], sessions)
        self.connection.fetch.assert_not_awaited()

    async def test_base_name_applies_to_fragments_including_empty_name(self):
        self.connection.fetch.return_value = [
            {'session_id': 'retained', 'event_name': ''},
            {'session_id': 'retained_reset_123', 'event_name': 'Old fragment name'},
        ]
        names = await self.server.get_saved_event_names(['retained_reset_123'])
        self.assertEqual({'retained_reset_123': ''}, names)

    async def test_database_failure_keeps_cached_name(self):
        self.connection.fetch.side_effect = RuntimeError('database offline')
        sessions = [{'sessionId': 'retained', 'eventName': 'Cached name'}]
        with self.assertLogs(level='ERROR'):
            await self.server.apply_saved_event_names(sessions)
        self.assertEqual([{'sessionId': 'retained', 'eventName': 'Cached name'}], sessions)

    async def test_refresh_sends_names_only_and_picks_up_subsequent_rename(self):
        self.server.connected_clients.add(object())
        self.server.tracking_history['retained'].append({'eventName': 'Old name'})
        self.connection.fetch.return_value = [{'session_id': 'retained', 'event_name': 'First name'}]
        await self.server.refresh_session_names()
        self.server.broadcast_update.assert_awaited_with({
            'type': 'session_names', 'names': {'retained': 'First name'}
        })
        self.connection.fetch.return_value = [{'session_id': 'retained', 'event_name': 'Renamed'}]
        await self.server.refresh_session_names()
        self.server.broadcast_update.assert_awaited_with({
            'type': 'session_names', 'names': {'retained': 'Renamed'}
        })
        self.assertEqual([], self.server.redis_client.mock_calls)

    async def test_no_clients_skips_polling(self):
        await self.server.refresh_session_names()
        self.connection.fetch.assert_not_awaited()
        self.server.broadcast_update.assert_not_awaited()

    async def test_history_and_list_broadcast_use_saved_name_for_redis_members_only(self):
        self.connection.fetch.return_value = [{'session_id': 'retained', 'event_name': 'Renamed'}]
        self.server.get_tracking_points_from_redis = AsyncMock(return_value=[{
            'sessionId': 'retained', 'eventName': 'Old name',
            'timestamp': '01-10-2026 12:00:00'
        }])
        client = AsyncMock()
        await self.server.send_history(client)
        messages = [json.loads(call.args[0]) for call in client.send.await_args_list]
        sessions = next(message['sessions'] for message in messages if message['type'] == 'session_list')
        self.assertEqual(['retained'], [session['sessionId'] for session in sessions])
        self.assertEqual('Renamed', sessions[0]['eventName'])

        await self.server.broadcast_session_list_update()
        self.assertEqual(sessions, self.server.broadcast_update.await_args.args[0]['sessions'])


class RedisBackfillTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.server = websocket_server.TrackingServer()
        self.server.db_pool = object()
        self.server.redis_client = AsyncMock()

    async def test_nonempty_redis_history_is_not_overwritten(self):
        self.server.redis_client.zcard.return_value = 3
        self.server.load_tracking_history_from_db = AsyncMock()

        restored = await self.server.backfill_empty_redis_history_from_db()

        self.assertEqual(0, restored)
        self.server.load_tracking_history_from_db.assert_not_awaited()
        self.server.redis_client.zadd.assert_not_awaited()

    async def test_empty_redis_history_is_backfilled_with_original_timestamp(self):
        self.server.redis_client.zcard.return_value = 0
        self.server.load_tracking_history_from_db = AsyncMock(return_value=1)
        self.server.tracking_history["session-1"].append({
            "sessionId": "session-1",
            "timestamp": "03-08-2026 18:45:54",
            "latitude": 48.2,
            "longitude": 16.3,
        })

        restored = await self.server.backfill_empty_redis_history_from_db()

        self.assertEqual(1, restored)
        self.server.redis_client.zadd.assert_awaited_once()
        key, entries = self.server.redis_client.zadd.await_args.args
        self.assertEqual(self.server.redis_history_key, key)
        self.assertEqual(1, len(entries))
        self.assertEqual(1785782754.0, next(iter(entries.values())))


if __name__ == "__main__":
    unittest.main()
