from datetime import datetime
from ..extensions import db


class LapTime(db.Model):
    """Lap time model matching existing PostgreSQL schema."""

    __tablename__ = 'lap_times'

    id = db.Column(db.Integer, primary_key=True)
    session_id = db.Column(db.String(255), db.ForeignKey('tracking_sessions.session_id', ondelete='CASCADE'), nullable=False)
    user_id = db.Column(db.Integer, db.ForeignKey('users.user_id'), nullable=True)
    lap_number = db.Column(db.Integer, nullable=False)
    start_time = db.Column(db.BigInteger, nullable=False)
    end_time = db.Column(db.BigInteger, nullable=False)
    # duration is a computed column in the database (end_time - start_time)
    distance = db.Column(db.Numeric(8, 4), nullable=False, default=1.0)
    created_at = db.Column(db.DateTime(timezone=True), default=datetime.utcnow)

    __table_args__ = (
        db.UniqueConstraint('session_id', 'lap_number', name='lap_times_session_id_lap_number_key'),
    )

    @property
    def duration(self):
        """Calculate duration (matching database computed column)."""
        return self.end_time - self.start_time

    def to_dict(self):
        return {
            'id': self.id,
            'session_id': self.session_id,
            'user_id': self.user_id,
            'lap_number': self.lap_number,
            'start_time': self.start_time,
            'end_time': self.end_time,
            'duration': self.duration,
            'distance': float(self.distance) if self.distance else None,
            'created_at': self.created_at.isoformat() if self.created_at else None,
        }

    def __repr__(self):
        return f'<LapTime {self.id}: Lap {self.lap_number} for {self.session_id}>'
