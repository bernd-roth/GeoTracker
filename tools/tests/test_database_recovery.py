import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location('recovery', pathlib.Path(__file__).parents[1] / 'database_recovery.py')
recovery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recovery)


class MatchingTest(unittest.TestCase):
    def event(self, session='s', date='2026-01-01', start=100000000):
        return {'sessionId': session, 'eventDate': date, 'stats': {'start': start}}

    def test_reused_session_on_other_date_is_not_same_event(self):
        points={(48.0,16.0),(48.1,16.1)}
        self.assertEqual(0, recovery.match_score(self.event(),self.event(date='2026-01-02'),points,points))

    def test_different_route_with_same_session_is_not_same_event(self):
        self.assertEqual(0, recovery.match_score(self.event(),self.event(),{(48,16)},{(49,17)}))

    def test_download_timezone_shift_and_subset_can_match(self):
        old={(48.0,16.0),(48.1,16.1),(48.2,16.2)}
        self.assertGreater(recovery.match_score(self.event(),self.event(start=92800000),old,{(48.1,16.1),(48.2,16.2)}),1)

    def test_renamed_session_can_match_recording(self):
        points={(48.0,16.0),(48.1,16.1)}
        self.assertGreater(recovery.match_score(self.event(session=None),self.event(session='new'),points,points),1)

    def test_empty_routes_do_not_match(self):
        self.assertEqual(0,recovery.match_score(self.event(),self.event(),set(),set()))


if __name__ == '__main__':
    unittest.main()
