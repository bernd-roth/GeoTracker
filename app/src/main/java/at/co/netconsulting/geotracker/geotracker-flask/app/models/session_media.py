from datetime import datetime
from ..extensions import db


class SessionMedia(db.Model):
    """Media attachments (photos/videos) for tracking sessions."""

    __tablename__ = 'session_media'

    media_id = db.Column(db.Integer, primary_key=True)
    session_id = db.Column(db.String(255), db.ForeignKey('tracking_sessions.session_id', ondelete='CASCADE'), nullable=False)
    media_uuid = db.Column(db.String(36), unique=True, nullable=False)  # UUID for file reference
    media_type = db.Column(db.String(10), nullable=False)  # 'image' or 'video'
    file_extension = db.Column(db.String(10), nullable=False)
    original_filename = db.Column(db.String(255), nullable=True)
    file_size_bytes = db.Column(db.BigInteger, nullable=True)
    thumbnail_generated = db.Column(db.Boolean, default=False)
    caption = db.Column(db.Text, nullable=True)
    sort_order = db.Column(db.Integer, default=0)
    created_at = db.Column(db.DateTime(timezone=True), default=datetime.utcnow)

    # Relationship back to session
    session = db.relationship('TrackingSession', backref=db.backref('media', lazy='dynamic', cascade='all, delete-orphan'))

    def to_dict(self):
        return {
            'media_id': self.media_id,
            'session_id': self.session_id,
            'media_uuid': self.media_uuid,
            'media_type': self.media_type,
            'file_extension': self.file_extension,
            'original_filename': self.original_filename,
            'file_size_bytes': self.file_size_bytes,
            'thumbnail_generated': self.thumbnail_generated,
            'caption': self.caption,
            'sort_order': self.sort_order,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'thumbnail_url': f'/api/media/{self.media_uuid}/thumbnail' if self.thumbnail_generated else None,
            'full_url': f'/api/media/{self.media_uuid}'
        }

    def __repr__(self):
        return f'<SessionMedia {self.media_uuid} ({self.media_type})>'
