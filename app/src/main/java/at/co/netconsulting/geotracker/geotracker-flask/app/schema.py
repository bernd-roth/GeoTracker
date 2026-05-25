from sqlalchemy import text

from .extensions import db


def ensure_database_schema(app):
    """Apply small idempotent schema updates needed by the Flask API."""
    with app.app_context():
        db.session.execute(text("""
            ALTER TABLE IF EXISTS planned_events
            ADD COLUMN IF NOT EXISTS planned_event_end_date DATE
        """))
        db.session.commit()
        app.logger.info("Database schema verified")
