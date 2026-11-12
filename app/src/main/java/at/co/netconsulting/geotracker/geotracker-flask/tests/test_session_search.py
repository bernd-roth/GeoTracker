"""Exercise website name searches against the real session API and database query."""
from datetime import datetime
import re
import unittest

from flask import Flask

from app.api.sessions import sessions_bp
from app.extensions import db
from app.models import GPSTrackingPoint, TrackingSession, User


class SessionSearchTest(unittest.TestCase):
    def setUp(self):
        app = Flask(__name__)
        app.config.update(TESTING=True, SQLALCHEMY_DATABASE_URI='sqlite://')
        db.init_app(app)
        app.register_blueprint(sessions_bp, url_prefix='/api')
        context = app.app_context()
        context.push()
        self.addCleanup(db.engine.dispose)
        self.addCleanup(context.pop)
        self.addCleanup(db.session.remove)
        # SQLite equivalent of the PostgreSQL function used to count reset points.
        db.engine.raw_connection().create_function(
            'regexp_replace', 3, lambda value, pattern, replacement:
            re.sub(pattern, replacement, value))
        db.create_all()
        db.session.add_all([
            User(user_id=1, firstname='Alice', lastname='Walker'),
            User(user_id=2, firstname='Alice', lastname='Smith'),
            User(user_id=3, firstname='Solo', lastname=None),
        ])
        db.session.flush()
        for session_id, user_id in [('walker', 1), ('smith', 2), ('solo', 3),
                                    ('anonymous', None), ('walker_reset_1', 1)]:
            db.session.add(TrackingSession(
                session_id=session_id, user_id=user_id,
                event_name='Morning outing', sport_type='Running',
                start_city='Vienna', comment='Along the river',
                start_date_time=datetime(2026, 1, 1)))
            for _ in range(10):
                db.session.add(GPSTrackingPoint(
                    session_id=session_id, latitude=48.2, longitude=16.3,
                    current_speed=0, average_speed=0, max_speed=0,
                    moving_average_speed=0, distance=0))
        db.session.commit()
        self.client = app.test_client()

    def search(self, text, **params):
        response = self.client.get('/api/sessions', query_string={
            'search': text, 'per_page': 100, 'page': 1,
            'hide_resets': 'true', 'min_total_points': 10, **params,
        })
        self.assertEqual(200, response.status_code)
        return response.get_json()['data']

    def assert_matches(self, text, expected):
        data = self.search(text)
        self.assertEqual(set(expected), {s['session_id'] for s in data['sessions']})
        self.assertEqual(len(expected), data['pagination']['total'])

    def test_first_name_matches_all_participants_with_that_name(self):
        self.assert_matches('Alice', ['walker', 'smith'])

    def test_last_name(self):
        self.assert_matches('Walker', ['walker'])

    def test_full_name(self):
        self.assert_matches('Alice Walker', ['walker'])

    def test_case_partial_names_and_whitespace(self):
        for text in ['aLiCe wAlKeR', '  Alice   Walker  ', 'lice Walk', 'WALK']:
            with self.subTest(text=text):
                self.assert_matches(text, ['walker'])

    def test_missing_last_name(self):
        self.assert_matches('solo', ['solo'])

    def test_unknown_name(self):
        self.assert_matches('Nobody', [])

    def test_existing_metadata_search_includes_sessions_without_users(self):
        for text in ['Morning', 'Running', 'Vienna', 'river']:
            with self.subTest(text=text):
                self.assert_matches(text, ['walker', 'smith', 'solo', 'anonymous'])

    def test_user_filter_still_applies_to_name_search(self):
        data = self.search('Alice', user_id=2)
        self.assertEqual(['smith'], [s['session_id'] for s in data['sessions']])


if __name__ == '__main__':
    unittest.main()
