from sqlalchemy import text

from .extensions import db


def ensure_database_schema(app):
    """Apply small idempotent schema updates needed by the Flask API."""
    with app.app_context():
        db.session.execute(text("""
            ALTER TABLE IF EXISTS tracking_sessions
            DROP COLUMN IF EXISTS min_distance_meters,
            DROP COLUMN IF EXISTS min_time_seconds,
            DROP COLUMN IF EXISTS voice_announcement_interval
        """))
        db.session.execute(text("""
            DROP INDEX IF EXISTS idx_tracking_data_settings
        """))
        db.session.execute(text("""
            ALTER TABLE IF EXISTS tracking_data
            DROP COLUMN IF EXISTS min_distance_meters,
            DROP COLUMN IF EXISTS min_time_seconds,
            DROP COLUMN IF EXISTS voice_announcement_interval
        """))
        db.session.execute(text("""
            ALTER TABLE IF EXISTS planned_events
            ADD COLUMN IF NOT EXISTS planned_event_end_date DATE
        """))
        db.session.execute(text("""
            ALTER TABLE IF EXISTS gps_tracking_points
            ADD COLUMN IF NOT EXISTS cadence INTEGER
        """))
        db.session.commit()
        app.logger.info("Database schema verified")
