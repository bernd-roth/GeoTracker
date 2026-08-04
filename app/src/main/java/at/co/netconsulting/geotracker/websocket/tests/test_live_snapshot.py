import importlib.util
import logging
import logging.handlers
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import AsyncMock


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
