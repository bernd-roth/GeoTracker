import os
import uuid
import subprocess
from flask import Blueprint, request, jsonify, send_file, current_app
from werkzeug.utils import secure_filename
from ..extensions import db
from ..models import SessionMedia, TrackingSession

media_bp = Blueprint('media', __name__)

# Configuration
MEDIA_BASE_PATH = '/home/bernd/docker-container/geotracker/media'
ALLOWED_IMAGE_EXTENSIONS = {'jpg', 'jpeg', 'png', 'heic', 'heif'}
ALLOWED_VIDEO_EXTENSIONS = {'mp4', 'mov', 'avi', 'mkv'}
MAX_CONTENT_LENGTH = 500 * 1024 * 1024  # 500MB max file size

THUMBNAIL_MAX_SIZE = (400, 400)
THUMBNAIL_QUALITY = 80


def get_media_paths():
    """Get paths for media storage."""
    originals_path = os.path.join(MEDIA_BASE_PATH, 'originals')
    thumbnails_path = os.path.join(MEDIA_BASE_PATH, 'thumbnails')

    # Ensure directories exist
    os.makedirs(originals_path, exist_ok=True)
    os.makedirs(thumbnails_path, exist_ok=True)

    return originals_path, thumbnails_path


def get_file_paths(media_uuid, extension):
    """Get full file paths for a media item."""
    originals_path, thumbnails_path = get_media_paths()

    # Use first 2 chars of UUID as prefix directory for better file distribution
    prefix = media_uuid[:2]

    original_dir = os.path.join(originals_path, prefix)
    thumbnail_dir = os.path.join(thumbnails_path, prefix)

    os.makedirs(original_dir, exist_ok=True)
    os.makedirs(thumbnail_dir, exist_ok=True)

    original_path = os.path.join(original_dir, f'{media_uuid}.{extension}')
    thumbnail_path = os.path.join(thumbnail_dir, f'{media_uuid}_thumb.jpg')

    return original_path, thumbnail_path


def get_extension(filename):
    """Get file extension from filename."""
    if '.' in filename:
        return filename.rsplit('.', 1)[1].lower()
    return ''


def is_allowed_file(filename, media_type):
    """Check if file extension is allowed for the given media type."""
    ext = get_extension(filename)
    if media_type == 'image':
        return ext in ALLOWED_IMAGE_EXTENSIONS
    elif media_type == 'video':
        return ext in ALLOWED_VIDEO_EXTENSIONS
    return False


def generate_image_thumbnail(original_path, thumbnail_path):
    """Generate thumbnail for image using PIL."""
    try:
        from PIL import Image

        with Image.open(original_path) as img:
            # Handle EXIF orientation
            try:
                from PIL import ExifTags
                for orientation in ExifTags.TAGS.keys():
                    if ExifTags.TAGS[orientation] == 'Orientation':
                        break
                exif = img._getexif()
                if exif is not None:
                    orientation_value = exif.get(orientation)
                    if orientation_value == 3:
                        img = img.rotate(180, expand=True)
                    elif orientation_value == 6:
                        img = img.rotate(270, expand=True)
                    elif orientation_value == 8:
                        img = img.rotate(90, expand=True)
            except (AttributeError, KeyError, IndexError):
                pass

            # Convert to RGB if necessary (for HEIC/PNG with alpha)
            if img.mode in ('RGBA', 'P', 'LA'):
                img = img.convert('RGB')
            elif img.mode != 'RGB':
                img = img.convert('RGB')

            # Calculate thumbnail size maintaining aspect ratio
            img.thumbnail(THUMBNAIL_MAX_SIZE, Image.Resampling.LANCZOS)

            # Save thumbnail
            img.save(thumbnail_path, 'JPEG', quality=THUMBNAIL_QUALITY, optimize=True)

        return True
    except Exception as e:
        current_app.logger.error(f'Error generating image thumbnail: {e}')
        return False


def generate_video_thumbnail(original_path, thumbnail_path):
    """Generate thumbnail for video using ffmpeg."""
    try:
        # Extract first frame at 1 second (or 0 if video is very short)
        cmd = [
            'ffmpeg',
            '-i', original_path,
            '-ss', '00:00:01',
            '-vframes', '1',
            '-vf', f'scale={THUMBNAIL_MAX_SIZE[0]}:{THUMBNAIL_MAX_SIZE[1]}:force_original_aspect_ratio=decrease',
            '-y',
            thumbnail_path
        ]

        result = subprocess.run(cmd, capture_output=True, timeout=30)

        if result.returncode != 0:
            # Try at 0 seconds if 1 second failed
            cmd[4] = '00:00:00'
            result = subprocess.run(cmd, capture_output=True, timeout=30)

        return result.returncode == 0 and os.path.exists(thumbnail_path)
    except subprocess.TimeoutExpired:
        current_app.logger.error('Video thumbnail generation timed out')
        return False
    except Exception as e:
        current_app.logger.error(f'Error generating video thumbnail: {e}')
        return False


@media_bp.route('/sessions/<session_id>/media', methods=['POST'])
def upload_media(session_id):
    """Upload media file for a session."""
    # Verify session exists
    session = TrackingSession.query.get(session_id)
    if not session:
        return jsonify({'success': False, 'error': 'Session not found'}), 404

    # Check if file was uploaded
    if 'file' not in request.files:
        return jsonify({'success': False, 'error': 'No file provided'}), 400

    file = request.files['file']
    if file.filename == '':
        return jsonify({'success': False, 'error': 'No file selected'}), 400

    # Get media type from form or detect from extension
    media_type = request.form.get('media_type', '').lower()
    original_filename = secure_filename(file.filename)
    extension = get_extension(original_filename)

    # Auto-detect media type if not provided
    if not media_type:
        if extension in ALLOWED_IMAGE_EXTENSIONS:
            media_type = 'image'
        elif extension in ALLOWED_VIDEO_EXTENSIONS:
            media_type = 'video'
        else:
            return jsonify({'success': False, 'error': 'Unsupported file type'}), 400

    # Validate file type
    if not is_allowed_file(original_filename, media_type):
        return jsonify({
            'success': False,
            'error': f'File type not allowed for {media_type}. Allowed: {ALLOWED_IMAGE_EXTENSIONS if media_type == "image" else ALLOWED_VIDEO_EXTENSIONS}'
        }), 400

    # Generate UUID for the media
    media_uuid = str(uuid.uuid4())

    # Get file paths
    original_path, thumbnail_path = get_file_paths(media_uuid, extension)

    try:
        # Save original file
        file.save(original_path)
        file_size = os.path.getsize(original_path)

        # Generate thumbnail
        if media_type == 'image':
            thumbnail_generated = generate_image_thumbnail(original_path, thumbnail_path)
        else:
            thumbnail_generated = generate_video_thumbnail(original_path, thumbnail_path)

        # Get caption and sort order from form
        caption = request.form.get('caption', '')
        sort_order = request.form.get('sort_order', 0, type=int)

        # Create database record
        media = SessionMedia(
            session_id=session_id,
            media_uuid=media_uuid,
            media_type=media_type,
            file_extension=extension,
            original_filename=original_filename,
            file_size_bytes=file_size,
            thumbnail_generated=thumbnail_generated,
            caption=caption,
            sort_order=sort_order
        )

        db.session.add(media)
        db.session.commit()

        return jsonify({
            'success': True,
            'message': 'Media uploaded successfully',
            'data': media.to_dict()
        }), 201

    except Exception as e:
        # Cleanup files on error
        if os.path.exists(original_path):
            os.remove(original_path)
        if os.path.exists(thumbnail_path):
            os.remove(thumbnail_path)

        db.session.rollback()
        current_app.logger.error(f'Error uploading media: {e}')
        return jsonify({'success': False, 'error': str(e)}), 500


@media_bp.route('/sessions/<session_id>/media', methods=['GET'])
def list_session_media(session_id):
    """List all media for a session."""
    # Verify session exists
    session = TrackingSession.query.get(session_id)
    if not session:
        return jsonify({'success': False, 'error': 'Session not found'}), 404

    media_list = SessionMedia.query.filter_by(session_id=session_id).order_by(SessionMedia.sort_order, SessionMedia.created_at).all()

    return jsonify({
        'success': True,
        'data': {
            'session_id': session_id,
            'media_count': len(media_list),
            'media': [m.to_dict() for m in media_list]
        }
    })


@media_bp.route('/media/<media_uuid>', methods=['GET'])
def get_media(media_uuid):
    """Get original media file."""
    media = SessionMedia.query.filter_by(media_uuid=media_uuid).first()
    if not media:
        return jsonify({'success': False, 'error': 'Media not found'}), 404

    original_path, _ = get_file_paths(media_uuid, media.file_extension)

    if not os.path.exists(original_path):
        return jsonify({'success': False, 'error': 'Media file not found on disk'}), 404

    # Determine MIME type
    mime_types = {
        'jpg': 'image/jpeg',
        'jpeg': 'image/jpeg',
        'png': 'image/png',
        'heic': 'image/heic',
        'heif': 'image/heif',
        'mp4': 'video/mp4',
        'mov': 'video/quicktime',
        'avi': 'video/x-msvideo',
        'mkv': 'video/x-matroska'
    }

    mimetype = mime_types.get(media.file_extension, 'application/octet-stream')

    return send_file(
        original_path,
        mimetype=mimetype,
        as_attachment=False,
        download_name=media.original_filename or f'{media_uuid}.{media.file_extension}'
    )


@media_bp.route('/media/<media_uuid>/thumbnail', methods=['GET'])
def get_thumbnail(media_uuid):
    """Get thumbnail for media."""
    media = SessionMedia.query.filter_by(media_uuid=media_uuid).first()
    if not media:
        return jsonify({'success': False, 'error': 'Media not found'}), 404

    _, thumbnail_path = get_file_paths(media_uuid, media.file_extension)

    if not os.path.exists(thumbnail_path):
        # Try to regenerate thumbnail
        original_path, _ = get_file_paths(media_uuid, media.file_extension)
        if os.path.exists(original_path):
            if media.media_type == 'image':
                generated = generate_image_thumbnail(original_path, thumbnail_path)
            else:
                generated = generate_video_thumbnail(original_path, thumbnail_path)

            if generated:
                media.thumbnail_generated = True
                db.session.commit()
            else:
                return jsonify({'success': False, 'error': 'Could not generate thumbnail'}), 500
        else:
            return jsonify({'success': False, 'error': 'Original media file not found'}), 404

    return send_file(
        thumbnail_path,
        mimetype='image/jpeg',
        as_attachment=False
    )


@media_bp.route('/media/<media_uuid>', methods=['DELETE'])
def delete_media(media_uuid):
    """Delete media file."""
    media = SessionMedia.query.filter_by(media_uuid=media_uuid).first()
    if not media:
        return jsonify({'success': False, 'error': 'Media not found'}), 404

    original_path, thumbnail_path = get_file_paths(media_uuid, media.file_extension)

    try:
        # Delete files
        if os.path.exists(original_path):
            os.remove(original_path)
        if os.path.exists(thumbnail_path):
            os.remove(thumbnail_path)

        # Delete database record
        db.session.delete(media)
        db.session.commit()

        return jsonify({
            'success': True,
            'message': 'Media deleted successfully'
        })

    except Exception as e:
        db.session.rollback()
        current_app.logger.error(f'Error deleting media: {e}')
        return jsonify({'success': False, 'error': str(e)}), 500


@media_bp.route('/media/<media_uuid>', methods=['PUT'])
def update_media(media_uuid):
    """Update media metadata (caption, sort_order)."""
    media = SessionMedia.query.filter_by(media_uuid=media_uuid).first()
    if not media:
        return jsonify({'success': False, 'error': 'Media not found'}), 404

    data = request.get_json()

    if 'caption' in data:
        media.caption = data['caption']
    if 'sort_order' in data:
        media.sort_order = data['sort_order']

    try:
        db.session.commit()
        return jsonify({
            'success': True,
            'message': 'Media updated successfully',
            'data': media.to_dict()
        })
    except Exception as e:
        db.session.rollback()
        current_app.logger.error(f'Error updating media: {e}')
        return jsonify({'success': False, 'error': str(e)}), 500
