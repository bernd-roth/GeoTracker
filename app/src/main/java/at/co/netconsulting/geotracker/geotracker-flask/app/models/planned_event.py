from datetime import datetime
from ..extensions import db


class PlannedEvent(db.Model):
    """PlannedEvent model matching existing PostgreSQL schema."""

    __tablename__ = 'planned_events'

    planned_event_id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('users.user_id'), nullable=True)
    planned_event_name = db.Column(db.String(255), nullable=False)
    planned_event_date = db.Column(db.Date, nullable=True)
    planned_event_type = db.Column(db.String(100), nullable=True)
    planned_event_country = db.Column(db.String(100), nullable=True)
    planned_event_city = db.Column(db.String(100), nullable=True)
    planned_latitude = db.Column(db.Float, nullable=True)
    planned_longitude = db.Column(db.Float, nullable=True)
    is_entered_and_finished = db.Column(db.Boolean, default=False)
    website = db.Column(db.String(500), nullable=True)
    comment = db.Column(db.Text, nullable=True)
    reminder_date_time = db.Column(db.DateTime, nullable=True)
    is_reminder_active = db.Column(db.Boolean, default=False)
    is_recurring = db.Column(db.Boolean, default=False)
    recurring_type = db.Column(db.String(50), nullable=True)
    recurring_interval = db.Column(db.Integer, default=1)
    recurring_end_date = db.Column(db.Date, nullable=True)
    recurring_days_of_week = db.Column(db.String(50), nullable=True)
    created_by_user_id = db.Column(db.Integer, db.ForeignKey('users.user_id'), nullable=True)
    is_public = db.Column(db.Boolean, default=True)
    created_at = db.Column(db.DateTime(timezone=True), default=datetime.utcnow)

    # Relationships
    user = db.relationship('User', foreign_keys=[user_id], backref='planned_events')
    created_by = db.relationship('User', foreign_keys=[created_by_user_id])

    def to_dict(self):
        return {
            'planned_event_id': self.planned_event_id,
            'user_id': self.user_id,
            'planned_event_name': self.planned_event_name,
            'planned_event_date': self.planned_event_date.isoformat() if self.planned_event_date else None,
            'planned_event_type': self.planned_event_type,
            'planned_event_country': self.planned_event_country,
            'planned_event_city': self.planned_event_city,
            'planned_latitude': self.planned_latitude,
            'planned_longitude': self.planned_longitude,
            'is_entered_and_finished': self.is_entered_and_finished,
            'website': self.website,
            'comment': self.comment,
            'reminder_date_time': self.reminder_date_time.isoformat() if self.reminder_date_time else None,
            'is_reminder_active': self.is_reminder_active,
            'is_recurring': self.is_recurring,
            'recurring_type': self.recurring_type,
            'recurring_interval': self.recurring_interval,
            'recurring_end_date': self.recurring_end_date.isoformat() if self.recurring_end_date else None,
            'recurring_days_of_week': self.recurring_days_of_week,
            'created_by_user_id': self.created_by_user_id,
            'is_public': self.is_public,
            'created_at': self.created_at.isoformat() if self.created_at else None,
        }

    def __repr__(self):
        return f'<PlannedEvent {self.planned_event_id}: {self.planned_event_name}>'
