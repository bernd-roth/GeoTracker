from datetime import datetime
from ..extensions import db


class HeartRateDevice(db.Model):
    """Heart rate device model matching existing PostgreSQL schema."""

    __tablename__ = 'heart_rate_devices'

    device_id = db.Column(db.Integer, primary_key=True)
    device_name = db.Column(db.String(100), nullable=False, unique=True)
    created_at = db.Column(db.DateTime(timezone=True), default=datetime.utcnow)

    # Relationships
    gps_points = db.relationship('GPSTrackingPoint', backref='heart_rate_device', lazy='dynamic')

    def to_dict(self):
        return {
            'device_id': self.device_id,
            'device_name': self.device_name,
            'created_at': self.created_at.isoformat() if self.created_at else None,
        }

    def __repr__(self):
        return f'<HeartRateDevice {self.device_id}: {self.device_name}>'
