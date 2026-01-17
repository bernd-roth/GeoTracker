from datetime import datetime
from ..extensions import db


class TrackingSession(db.Model):
    """Tracking session model matching existing PostgreSQL schema."""

    __tablename__ = 'tracking_sessions'

    session_id = db.Column(db.String(255), primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('users.user_id', ondelete='CASCADE'), nullable=True)
    event_name = db.Column(db.String(255), nullable=True)
    sport_type = db.Column(db.String(100), nullable=True)
    comment = db.Column(db.Text, nullable=True)
    clothing = db.Column(db.String(255), nullable=True)
    start_date_time = db.Column(db.DateTime(timezone=True), nullable=True)
    min_distance_meters = db.Column(db.Integer, nullable=True)
    min_time_seconds = db.Column(db.Integer, nullable=True)
    voice_announcement_interval = db.Column(db.Integer, nullable=True)
    created_at = db.Column(db.DateTime(timezone=True), default=datetime.utcnow)
    updated_at = db.Column(db.DateTime(timezone=True), default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    gps_points = db.relationship('GPSTrackingPoint', backref='session', lazy='dynamic',
                                  cascade='all, delete-orphan')
    lap_times = db.relationship('LapTime', backref='session', lazy='dynamic',
                                 cascade='all, delete-orphan')

    def to_dict(self, include_stats=False):
        result = {
            'session_id': self.session_id,
            'user_id': self.user_id,
            'event_name': self.event_name,
            'sport_type': self.sport_type,
            'comment': self.comment,
            'clothing': self.clothing,
            'start_date_time': self.start_date_time.isoformat() if self.start_date_time else None,
            'min_distance_meters': self.min_distance_meters,
            'min_time_seconds': self.min_time_seconds,
            'voice_announcement_interval': self.voice_announcement_interval,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None,
        }

        if include_stats:
            result['gps_point_count'] = self.gps_points.count()
            result['lap_count'] = self.lap_times.count()

        return result

    def to_dict_with_details(self):
        """Return session with GPS points and lap times."""
        result = self.to_dict(include_stats=True)
        result['gps_points'] = [p.to_dict() for p in self.gps_points.order_by('received_at').all()]
        result['lap_times'] = [l.to_dict() for l in self.lap_times.order_by('lap_number').all()]
        return result

    def __repr__(self):
        return f'<TrackingSession {self.session_id}>'
