from datetime import datetime
from ..extensions import db


class GPSTrackingPoint(db.Model):
    """GPS tracking point model matching existing PostgreSQL schema."""

    __tablename__ = 'gps_tracking_points'

    id = db.Column(db.Integer, primary_key=True)
    session_id = db.Column(db.String(255), db.ForeignKey('tracking_sessions.session_id', ondelete='CASCADE'))
    latitude = db.Column(db.Numeric(10, 8), nullable=False)
    longitude = db.Column(db.Numeric(11, 8), nullable=False)
    altitude = db.Column(db.Numeric(10, 4), nullable=True)
    horizontal_accuracy = db.Column(db.Numeric(8, 4), nullable=True)
    vertical_accuracy_meters = db.Column(db.Numeric(8, 4), nullable=True)
    number_of_satellites = db.Column(db.Integer, nullable=True)
    used_number_of_satellites = db.Column(db.Integer, nullable=True)
    current_speed = db.Column(db.Numeric(8, 4), nullable=False)
    average_speed = db.Column(db.Numeric(8, 4), nullable=False)
    max_speed = db.Column(db.Numeric(8, 4), nullable=False)
    moving_average_speed = db.Column(db.Numeric(8, 4), nullable=False)
    speed = db.Column(db.Numeric(8, 4), nullable=True)
    speed_accuracy_meters_per_second = db.Column(db.Numeric(8, 4), nullable=True)
    distance = db.Column(db.Numeric(12, 4), nullable=False)
    covered_distance = db.Column(db.Numeric(12, 4), nullable=True)
    cumulative_elevation_gain = db.Column(db.Numeric(10, 4), nullable=True)
    heart_rate = db.Column(db.Integer, nullable=True)
    heart_rate_device_id = db.Column(db.Integer, db.ForeignKey('heart_rate_devices.device_id'), nullable=True)
    lap = db.Column(db.Integer, default=0)
    received_at = db.Column(db.DateTime(timezone=True), default=datetime.utcnow)
    created_at = db.Column(db.DateTime(timezone=True), default=datetime.utcnow)
    temperature = db.Column(db.Numeric(5, 2), nullable=True)
    wind_speed = db.Column(db.Numeric(6, 2), nullable=True)
    wind_direction = db.Column(db.Numeric(5, 1), nullable=True)
    humidity = db.Column(db.Integer, nullable=True)
    weather_timestamp = db.Column(db.BigInteger, nullable=True)
    weather_code = db.Column(db.Integer, nullable=True)
    pressure = db.Column(db.Numeric(8, 2), nullable=True)
    pressure_accuracy = db.Column(db.Integer, nullable=True)
    altitude_from_pressure = db.Column(db.Numeric(10, 4), nullable=True)
    sea_level_pressure = db.Column(db.Numeric(8, 2), nullable=True)
    slope = db.Column(db.Numeric(6, 2), nullable=True)
    average_slope = db.Column(db.Numeric(6, 2), nullable=True)
    max_uphill_slope = db.Column(db.Numeric(6, 2), nullable=True)
    max_downhill_slope = db.Column(db.Numeric(6, 2), nullable=True)

    def to_dict(self):
        return {
            'id': self.id,
            'session_id': self.session_id,
            'latitude': float(self.latitude) if self.latitude else None,
            'longitude': float(self.longitude) if self.longitude else None,
            'altitude': float(self.altitude) if self.altitude else None,
            'horizontal_accuracy': float(self.horizontal_accuracy) if self.horizontal_accuracy else None,
            'vertical_accuracy_meters': float(self.vertical_accuracy_meters) if self.vertical_accuracy_meters else None,
            'number_of_satellites': self.number_of_satellites,
            'used_number_of_satellites': self.used_number_of_satellites,
            'current_speed': float(self.current_speed) if self.current_speed else None,
            'average_speed': float(self.average_speed) if self.average_speed else None,
            'max_speed': float(self.max_speed) if self.max_speed else None,
            'moving_average_speed': float(self.moving_average_speed) if self.moving_average_speed else None,
            'speed': float(self.speed) if self.speed else None,
            'speed_accuracy_meters_per_second': float(self.speed_accuracy_meters_per_second) if self.speed_accuracy_meters_per_second else None,
            'distance': float(self.distance) if self.distance else None,
            'covered_distance': float(self.covered_distance) if self.covered_distance else None,
            'cumulative_elevation_gain': float(self.cumulative_elevation_gain) if self.cumulative_elevation_gain else None,
            'heart_rate': self.heart_rate,
            'heart_rate_device_id': self.heart_rate_device_id,
            'lap': self.lap,
            'received_at': self.received_at.isoformat() if self.received_at else None,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'temperature': float(self.temperature) if self.temperature else None,
            'wind_speed': float(self.wind_speed) if self.wind_speed else None,
            'wind_direction': float(self.wind_direction) if self.wind_direction else None,
            'humidity': self.humidity,
            'weather_timestamp': self.weather_timestamp,
            'weather_code': self.weather_code,
            'pressure': float(self.pressure) if self.pressure else None,
            'pressure_accuracy': self.pressure_accuracy,
            'altitude_from_pressure': float(self.altitude_from_pressure) if self.altitude_from_pressure else None,
            'sea_level_pressure': float(self.sea_level_pressure) if self.sea_level_pressure else None,
            'slope': float(self.slope) if self.slope else None,
            'average_slope': float(self.average_slope) if self.average_slope else None,
            'max_uphill_slope': float(self.max_uphill_slope) if self.max_uphill_slope else None,
            'max_downhill_slope': float(self.max_downhill_slope) if self.max_downhill_slope else None,
        }

    def __repr__(self):
        return f'<GPSTrackingPoint {self.id} @ ({self.latitude}, {self.longitude})>'
