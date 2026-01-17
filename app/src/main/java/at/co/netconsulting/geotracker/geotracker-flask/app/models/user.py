from datetime import datetime
from ..extensions import db


class User(db.Model):
    """User model matching existing PostgreSQL schema."""

    __tablename__ = 'users'

    user_id = db.Column(db.Integer, primary_key=True)
    firstname = db.Column(db.String(100), nullable=False)
    lastname = db.Column(db.String(100), nullable=True)
    birthdate = db.Column(db.String(20), nullable=True)
    height = db.Column(db.Numeric(5, 2), nullable=True)
    weight = db.Column(db.Numeric(5, 2), nullable=True)
    created_at = db.Column(db.DateTime(timezone=True), default=datetime.utcnow)
    updated_at = db.Column(db.DateTime(timezone=True), default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    sessions = db.relationship('TrackingSession', backref='user', lazy='dynamic')
    lap_times = db.relationship('LapTime', backref='user', lazy='dynamic')

    def to_dict(self):
        return {
            'user_id': self.user_id,
            'firstname': self.firstname,
            'lastname': self.lastname,
            'birthdate': self.birthdate,
            'height': float(self.height) if self.height else None,
            'weight': float(self.weight) if self.weight else None,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None,
        }

    def __repr__(self):
        return f'<User {self.user_id}: {self.firstname} {self.lastname}>'
