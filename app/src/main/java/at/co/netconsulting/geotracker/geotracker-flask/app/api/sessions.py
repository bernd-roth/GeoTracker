from flask import Blueprint, request, current_app
from datetime import datetime
from dateutil import parser as date_parser
from ..extensions import db
from ..models import TrackingSession, User, GPSTrackingPoint, LapTime
from ..utils.responses import success_response, error_response, paginated_response
from .errors import NotFoundError, ValidationError, ConflictError

sessions_bp = Blueprint('sessions', __name__)


# IMPORTANT: Static routes must be defined before dynamic routes to avoid conflicts
@sessions_bp.route('/sessions/find', methods=['GET'])
def find_session():
    """Find a session by date and user info.

    Query params:
        date: Event date in YYYY-MM-DD format
        firstname: User's first name
        lastname: User's last name (optional)
        birthdate: User's birthdate (optional)

    Returns the session_id if found, or null if not found.
    """
    event_date = request.args.get('date')
    firstname = request.args.get('firstname')
    lastname = request.args.get('lastname')
    birthdate = request.args.get('birthdate')

    if not event_date or not firstname:
        return error_response("date and firstname are required", 400)

    # Find user
    user_query = User.query.filter_by(firstname=firstname)
    if lastname:
        user_query = user_query.filter_by(lastname=lastname)
    if birthdate:
        user_query = user_query.filter_by(birthdate=birthdate)

    user = user_query.first()

    if not user:
        return success_response(data={'found': False, 'session_id': None})

    # Parse the date and find sessions on that date
    try:
        from datetime import timedelta
        search_date = date_parser.parse(event_date).date()
        start_of_day = datetime.combine(search_date, datetime.min.time())
        end_of_day = start_of_day + timedelta(days=1)

        session = TrackingSession.query.filter(
            TrackingSession.user_id == user.user_id,
            TrackingSession.start_date_time >= start_of_day,
            TrackingSession.start_date_time < end_of_day
        ).first()

        if session:
            return success_response(data={
                'found': True,
                'session_id': session.session_id,
                'event_name': session.event_name,
                'sport_type': session.sport_type
            })
        else:
            return success_response(data={'found': False, 'session_id': None})

    except Exception as e:
        current_app.logger.error(f"Error finding session: {e}")
        return error_response(f"Error parsing date: {str(e)}", 400)


@sessions_bp.route('/sessions/<session_id>/exists', methods=['GET'])
def check_session_exists(session_id):
    """Check if a session exists."""
    session = TrackingSession.query.get(session_id)
    return success_response(data={'exists': session is not None})


@sessions_bp.route('/sessions/<session_id>', methods=['GET'])
def get_session(session_id):
    """Get session details with optional GPS points and lap times."""
    session = TrackingSession.query.get(session_id)

    if not session:
        raise NotFoundError(f"Session '{session_id}' not found")

    include_details = request.args.get('include_details', 'false').lower() == 'true'

    if include_details:
        return success_response(data=session.to_dict_with_details())

    return success_response(data=session.to_dict(include_stats=True))


@sessions_bp.route('/sessions', methods=['GET'])
def list_sessions():
    """List sessions with pagination.

    Query params:
        page: Page number (default: 1)
        per_page: Items per page (default: 20, max: 100)
        user_id: Filter by user ID
        firstname: Filter by user's first name
        lastname: Filter by user's last name (optional, used with firstname)
        birthdate: Filter by user's birthdate (optional, used with firstname)
    """
    page = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 20, type=int)
    user_id = request.args.get('user_id', type=int)
    firstname = request.args.get('firstname')
    lastname = request.args.get('lastname')
    birthdate = request.args.get('birthdate')

    # Limit per_page to prevent abuse
    per_page = min(per_page, 100)

    query = TrackingSession.query

    if user_id:
        query = query.filter(TrackingSession.user_id == user_id)
    elif firstname:
        # Find user by name and filter by user_id
        user_query = User.query.filter_by(firstname=firstname)
        if lastname:
            user_query = user_query.filter_by(lastname=lastname)
        if birthdate:
            user_query = user_query.filter_by(birthdate=birthdate)
        user = user_query.first()
        if user:
            query = query.filter(TrackingSession.user_id == user.user_id)
        else:
            # User not found, return empty result
            return paginated_response(
                items=[],
                page=page,
                per_page=per_page,
                total=0,
                item_name='sessions'
            )

    # Order by start_date_time descending (most recent first)
    query = query.order_by(TrackingSession.start_date_time.desc())

    total = query.count()
    sessions = query.offset((page - 1) * per_page).limit(per_page).all()

    return paginated_response(
        items=[s.to_dict(include_stats=True) for s in sessions],
        page=page,
        per_page=per_page,
        total=total,
        item_name='sessions'
    )


@sessions_bp.route('/sessions', methods=['POST'])
def create_session():
    """Create a new tracking session."""
    data = request.get_json()

    if not data:
        raise ValidationError("Request body is required")

    session_id = data.get('session_id')
    if not session_id:
        raise ValidationError("session_id is required")

    # Check if session already exists
    existing = TrackingSession.query.get(session_id)
    if existing:
        raise ConflictError(f"Session '{session_id}' already exists")

    # Handle user - find or create
    user_id = None
    firstname = data.get('firstname')
    lastname = data.get('lastname')
    birthdate = data.get('birthdate')

    if firstname:
        user = User.query.filter_by(
            firstname=firstname,
            lastname=lastname,
            birthdate=birthdate
        ).first()

        if not user:
            user = User(
                firstname=firstname,
                lastname=lastname,
                birthdate=birthdate,
                height=data.get('height'),
                weight=data.get('weight')
            )
            db.session.add(user)
            db.session.flush()

        user_id = user.user_id

    # Parse start_date_time
    start_date_time = None
    if data.get('start_date_time'):
        try:
            start_date_time = date_parser.parse(data['start_date_time'])
        except (ValueError, TypeError) as e:
            current_app.logger.warning(f"Failed to parse start_date_time: {e}")

    # Create session
    session = TrackingSession(
        session_id=session_id,
        user_id=user_id,
        event_name=data.get('event_name'),
        sport_type=data.get('sport_type'),
        comment=data.get('comment'),
        clothing=data.get('clothing'),
        start_date_time=start_date_time,
        min_distance_meters=data.get('min_distance_meters'),
        min_time_seconds=data.get('min_time_seconds'),
        voice_announcement_interval=data.get('voice_announcement_interval'),
        # Location geocoding fields
        start_city=data.get('start_city'),
        start_country=data.get('start_country'),
        start_address=data.get('start_address'),
        end_city=data.get('end_city'),
        end_country=data.get('end_country'),
        end_address=data.get('end_address')
    )

    db.session.add(session)

    # Add GPS points if provided
    gps_points = data.get('gps_points', [])
    for point_data in gps_points:
        gps_point = create_gps_point(session_id, point_data)
        db.session.add(gps_point)

    # Add lap times if provided
    lap_times = data.get('lap_times', [])
    for lap_data in lap_times:
        lap_time = create_lap_time(session_id, user_id, lap_data)
        db.session.add(lap_time)

    try:
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        current_app.logger.error(f"Error creating session: {e}")
        raise ValidationError(f"Failed to create session: {str(e)}")

    return success_response(
        data=session.to_dict(include_stats=True),
        message="Session created successfully",
        status_code=201
    )


@sessions_bp.route('/sessions/<session_id>', methods=['PUT'])
def update_session(session_id):
    """Update session metadata."""
    session = TrackingSession.query.get(session_id)

    if not session:
        raise NotFoundError(f"Session '{session_id}' not found")

    data = request.get_json()
    if not data:
        raise ValidationError("Request body is required")

    # Update allowed fields
    allowed_fields = [
        'event_name', 'sport_type', 'comment', 'clothing',
        'min_distance_meters', 'min_time_seconds', 'voice_announcement_interval',
        'start_city', 'start_country', 'start_address',
        'end_city', 'end_country', 'end_address'
    ]

    for field in allowed_fields:
        if field in data:
            setattr(session, field, data[field])

    # Update timestamp
    session.updated_at = datetime.utcnow()

    try:
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        current_app.logger.error(f"Error updating session: {e}")
        raise ValidationError(f"Failed to update session: {str(e)}")

    return success_response(
        data=session.to_dict(include_stats=True),
        message="Session updated successfully"
    )


@sessions_bp.route('/sessions/<session_id>', methods=['DELETE'])
def delete_session(session_id):
    """Delete a session and all related data."""
    session = TrackingSession.query.get(session_id)

    if not session:
        raise NotFoundError(f"Session '{session_id}' not found")

    try:
        db.session.delete(session)
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        current_app.logger.error(f"Error deleting session: {e}")
        raise ValidationError(f"Failed to delete session: {str(e)}")

    return success_response(message=f"Session '{session_id}' deleted successfully")


def create_gps_point(session_id, data):
    """Create a GPS tracking point from data dict."""
    # Parse received_at if provided
    received_at = None
    if data.get('received_at'):
        try:
            received_at = date_parser.parse(data['received_at'])
        except (ValueError, TypeError):
            received_at = datetime.utcnow()

    return GPSTrackingPoint(
        session_id=session_id,
        latitude=data.get('latitude', 0),
        longitude=data.get('longitude', 0),
        altitude=data.get('altitude'),
        horizontal_accuracy=data.get('horizontal_accuracy'),
        vertical_accuracy_meters=data.get('vertical_accuracy_meters'),
        number_of_satellites=data.get('number_of_satellites'),
        used_number_of_satellites=data.get('used_number_of_satellites'),
        current_speed=data.get('current_speed', 0),
        average_speed=data.get('average_speed', 0),
        max_speed=data.get('max_speed', 0),
        moving_average_speed=data.get('moving_average_speed', 0),
        speed=data.get('speed'),
        speed_accuracy_meters_per_second=data.get('speed_accuracy_meters_per_second'),
        distance=data.get('distance', 0),
        covered_distance=data.get('covered_distance'),
        cumulative_elevation_gain=data.get('cumulative_elevation_gain'),
        heart_rate=data.get('heart_rate'),
        heart_rate_device_id=data.get('heart_rate_device_id'),
        lap=data.get('lap', 0),
        received_at=received_at,
        temperature=data.get('temperature'),
        wind_speed=data.get('wind_speed'),
        wind_direction=data.get('wind_direction'),
        humidity=data.get('humidity'),
        weather_timestamp=data.get('weather_timestamp'),
        weather_code=data.get('weather_code'),
        pressure=data.get('pressure'),
        pressure_accuracy=data.get('pressure_accuracy'),
        altitude_from_pressure=data.get('altitude_from_pressure'),
        sea_level_pressure=data.get('sea_level_pressure'),
        slope=data.get('slope'),
        average_slope=data.get('average_slope'),
        max_uphill_slope=data.get('max_uphill_slope'),
        max_downhill_slope=data.get('max_downhill_slope')
    )


def create_lap_time(session_id, user_id, data):
    """Create a lap time from data dict."""
    return LapTime(
        session_id=session_id,
        user_id=user_id,
        lap_number=data.get('lap_number', 0),
        start_time=data.get('start_time', 0),
        end_time=data.get('end_time', 0),
        distance=data.get('distance', 1.0)
    )
