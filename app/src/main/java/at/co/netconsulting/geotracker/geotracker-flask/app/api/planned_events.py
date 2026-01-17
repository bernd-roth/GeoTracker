from flask import Blueprint, request, current_app
from datetime import datetime
from dateutil import parser as date_parser
from ..extensions import db
from ..models import PlannedEvent, User
from ..utils.responses import success_response, error_response, paginated_response
from .errors import NotFoundError, ValidationError, ConflictError

planned_events_bp = Blueprint('planned_events', __name__)


def get_or_create_user(firstname, lastname=None, birthdate=None):
    """Find or create a user by firstname, lastname, and birthdate."""
    user = User.query.filter_by(
        firstname=firstname,
        lastname=lastname,
        birthdate=birthdate
    ).first()

    if not user:
        user = User(
            firstname=firstname,
            lastname=lastname,
            birthdate=birthdate
        )
        db.session.add(user)
        db.session.flush()

    return user


@planned_events_bp.route('/planned-events', methods=['GET'])
def list_planned_events():
    """List public planned events with pagination.

    Query params:
        page: Page number (default 1)
        per_page: Items per page (default 20, max 100)
        firstname: Filter to exclude events created by this user (optional)
        lastname: User's last name (optional)
        birthdate: User's birthdate (optional)
    """
    page = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 20, type=int)
    firstname = request.args.get('firstname')
    lastname = request.args.get('lastname')
    birthdate = request.args.get('birthdate')

    # Limit per_page to prevent abuse
    per_page = min(per_page, 100)

    query = PlannedEvent.query.filter(PlannedEvent.is_public == True)

    # Optionally exclude events created by requesting user
    if firstname:
        user = User.query.filter_by(
            firstname=firstname,
            lastname=lastname,
            birthdate=birthdate
        ).first()

        if user:
            query = query.filter(PlannedEvent.created_by_user_id != user.user_id)

    # Order by event date descending (most recent first)
    query = query.order_by(PlannedEvent.planned_event_date.desc())

    total = query.count()
    events = query.offset((page - 1) * per_page).limit(per_page).all()

    return paginated_response(
        items=[e.to_dict() for e in events],
        page=page,
        per_page=per_page,
        total=total,
        item_name='planned_events'
    )


@planned_events_bp.route('/planned-events/download', methods=['GET'])
def download_planned_events():
    """Download all public planned events excluding the requesting user's own events.

    Query params:
        firstname: User's first name (required)
        lastname: User's last name (optional)
        birthdate: User's birthdate (optional)
        exclude_user_events: Whether to exclude user's own events (default true)
    """
    firstname = request.args.get('firstname')
    lastname = request.args.get('lastname')
    birthdate = request.args.get('birthdate')
    exclude_user_events = request.args.get('exclude_user_events', 'true').lower() == 'true'

    if not firstname:
        return error_response("firstname is required", 400)

    query = PlannedEvent.query.filter(PlannedEvent.is_public == True)

    # Optionally exclude events created by requesting user
    if exclude_user_events:
        user = User.query.filter_by(
            firstname=firstname,
            lastname=lastname or '',
            birthdate=birthdate or ''
        ).first()

        if user:
            query = query.filter(PlannedEvent.created_by_user_id != user.user_id)

    # Order by event date
    query = query.order_by(PlannedEvent.planned_event_date.desc())

    events = query.all()

    return success_response(data={
        'events': [e.to_dict() for e in events],
        'count': len(events)
    })


@planned_events_bp.route('/planned-events/<int:event_id>', methods=['GET'])
def get_planned_event(event_id):
    """Get a specific planned event by ID."""
    event = PlannedEvent.query.get(event_id)

    if not event:
        raise NotFoundError(f"Planned event '{event_id}' not found")

    return success_response(data=event.to_dict())


@planned_events_bp.route('/planned-events', methods=['POST'])
def create_planned_event():
    """Create a new planned event."""
    data = request.get_json()

    if not data:
        raise ValidationError("Request body is required")

    planned_event_name = data.get('planned_event_name')
    if not planned_event_name:
        raise ValidationError("planned_event_name is required")

    # Handle user - find or create
    firstname = data.get('firstname')
    lastname = data.get('lastname')
    birthdate = data.get('birthdate')

    user_id = None
    if firstname:
        user = get_or_create_user(firstname, lastname, birthdate)
        user_id = user.user_id

    # Parse dates
    planned_event_date = None
    if data.get('planned_event_date'):
        try:
            planned_event_date = date_parser.parse(data['planned_event_date']).date()
        except (ValueError, TypeError) as e:
            current_app.logger.warning(f"Failed to parse planned_event_date: {e}")

    reminder_date_time = None
    if data.get('reminder_date_time'):
        try:
            reminder_date_time = date_parser.parse(data['reminder_date_time'])
        except (ValueError, TypeError) as e:
            current_app.logger.warning(f"Failed to parse reminder_date_time: {e}")

    recurring_end_date = None
    if data.get('recurring_end_date'):
        try:
            recurring_end_date = date_parser.parse(data['recurring_end_date']).date()
        except (ValueError, TypeError) as e:
            current_app.logger.warning(f"Failed to parse recurring_end_date: {e}")

    # Create event
    event = PlannedEvent(
        user_id=user_id,
        planned_event_name=planned_event_name,
        planned_event_date=planned_event_date,
        planned_event_type=data.get('planned_event_type'),
        planned_event_country=data.get('planned_event_country'),
        planned_event_city=data.get('planned_event_city'),
        planned_latitude=data.get('planned_latitude'),
        planned_longitude=data.get('planned_longitude'),
        is_entered_and_finished=data.get('is_entered_and_finished', False),
        website=data.get('website'),
        comment=data.get('comment'),
        reminder_date_time=reminder_date_time,
        is_reminder_active=data.get('is_reminder_active', False),
        is_recurring=data.get('is_recurring', False),
        recurring_type=data.get('recurring_type'),
        recurring_interval=data.get('recurring_interval', 1),
        recurring_end_date=recurring_end_date,
        recurring_days_of_week=data.get('recurring_days_of_week'),
        created_by_user_id=user_id,
        is_public=data.get('is_public', True)
    )

    db.session.add(event)

    try:
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        current_app.logger.error(f"Error creating planned event: {e}")
        raise ValidationError(f"Failed to create planned event: {str(e)}")

    return success_response(
        data=event.to_dict(),
        message="Planned event created successfully",
        status_code=201
    )


@planned_events_bp.route('/planned-events/upload', methods=['POST'])
def upload_planned_events():
    """Bulk upload planned events.

    Request body:
        events: List of event objects
        firstname: User's first name (required)
        lastname: User's last name (optional)
        birthdate: User's birthdate (optional)
    """
    data = request.get_json()

    if not data:
        raise ValidationError("Request body is required")

    events_data = data.get('events', [])
    firstname = data.get('firstname')
    lastname = data.get('lastname')
    birthdate = data.get('birthdate')

    if not firstname:
        return error_response("firstname is required", 400)

    if not events_data:
        return error_response("No events provided", 400)

    # Get or create user
    user = get_or_create_user(firstname, lastname, birthdate)
    user_id = user.user_id

    uploaded_count = 0
    duplicate_count = 0
    error_count = 0
    errors = []

    for event_data in events_data:
        try:
            # Parse event date
            planned_event_date = None
            if event_data.get('plannedEventDate'):
                try:
                    planned_event_date = date_parser.parse(event_data['plannedEventDate']).date()
                except Exception:
                    planned_event_date = None

            # Check for duplicates
            existing_event = PlannedEvent.query.filter(
                PlannedEvent.planned_event_name == event_data.get('plannedEventName', ''),
                PlannedEvent.planned_event_date == planned_event_date,
                PlannedEvent.planned_event_country == event_data.get('plannedEventCountry', ''),
                PlannedEvent.planned_event_city == event_data.get('plannedEventCity', ''),
                PlannedEvent.is_public == True
            ).first()

            if existing_event:
                duplicate_count += 1
                continue

            # Parse dates
            reminder_date_time = None
            if event_data.get('reminderDateTime'):
                try:
                    reminder_date_time = date_parser.parse(event_data['reminderDateTime'])
                except Exception:
                    reminder_date_time = None

            recurring_end_date = None
            if event_data.get('recurringEndDate'):
                try:
                    recurring_end_date = date_parser.parse(event_data['recurringEndDate']).date()
                except Exception:
                    recurring_end_date = None

            # Create event
            event = PlannedEvent(
                user_id=user_id,
                planned_event_name=event_data.get('plannedEventName', ''),
                planned_event_date=planned_event_date,
                planned_event_type=event_data.get('plannedEventType', ''),
                planned_event_country=event_data.get('plannedEventCountry', ''),
                planned_event_city=event_data.get('plannedEventCity', ''),
                planned_latitude=event_data.get('plannedLatitude'),
                planned_longitude=event_data.get('plannedLongitude'),
                is_entered_and_finished=event_data.get('isEnteredAndFinished', False),
                website=event_data.get('website', ''),
                comment=event_data.get('comment', ''),
                reminder_date_time=reminder_date_time,
                is_reminder_active=event_data.get('isReminderActive', False),
                is_recurring=event_data.get('isRecurring', False),
                recurring_type=event_data.get('recurringType', ''),
                recurring_interval=event_data.get('recurringInterval', 1),
                recurring_end_date=recurring_end_date,
                recurring_days_of_week=event_data.get('recurringDaysOfWeek', ''),
                created_by_user_id=user_id,
                is_public=True
            )

            db.session.add(event)
            uploaded_count += 1

        except Exception as e:
            error_count += 1
            errors.append(f"Event '{event_data.get('plannedEventName', 'Unknown')}': {str(e)}")
            current_app.logger.error(f"Error uploading event: {e}")

    try:
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        current_app.logger.error(f"Error committing planned events: {e}")
        return error_response(f"Failed to save events: {str(e)}", 500)

    current_app.logger.info(f"Planned events upload: {uploaded_count} uploaded, {duplicate_count} duplicates, {error_count} errors")

    return success_response(data={
        'uploaded_count': uploaded_count,
        'duplicate_count': duplicate_count,
        'error_count': error_count,
        'errors': errors
    }, message="Events uploaded successfully")


@planned_events_bp.route('/planned-events/<int:event_id>', methods=['PUT'])
def update_planned_event(event_id):
    """Update a planned event."""
    event = PlannedEvent.query.get(event_id)

    if not event:
        raise NotFoundError(f"Planned event '{event_id}' not found")

    data = request.get_json()
    if not data:
        raise ValidationError("Request body is required")

    # Update allowed fields
    if 'planned_event_name' in data:
        event.planned_event_name = data['planned_event_name']
    if 'planned_event_date' in data:
        try:
            event.planned_event_date = date_parser.parse(data['planned_event_date']).date() if data['planned_event_date'] else None
        except Exception:
            pass
    if 'planned_event_type' in data:
        event.planned_event_type = data['planned_event_type']
    if 'planned_event_country' in data:
        event.planned_event_country = data['planned_event_country']
    if 'planned_event_city' in data:
        event.planned_event_city = data['planned_event_city']
    if 'planned_latitude' in data:
        event.planned_latitude = data['planned_latitude']
    if 'planned_longitude' in data:
        event.planned_longitude = data['planned_longitude']
    if 'is_entered_and_finished' in data:
        event.is_entered_and_finished = data['is_entered_and_finished']
    if 'website' in data:
        event.website = data['website']
    if 'comment' in data:
        event.comment = data['comment']
    if 'is_public' in data:
        event.is_public = data['is_public']

    try:
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        current_app.logger.error(f"Error updating planned event: {e}")
        raise ValidationError(f"Failed to update planned event: {str(e)}")

    return success_response(
        data=event.to_dict(),
        message="Planned event updated successfully"
    )


@planned_events_bp.route('/planned-events/<int:event_id>', methods=['DELETE'])
def delete_planned_event(event_id):
    """Delete a planned event."""
    event = PlannedEvent.query.get(event_id)

    if not event:
        raise NotFoundError(f"Planned event '{event_id}' not found")

    try:
        db.session.delete(event)
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        current_app.logger.error(f"Error deleting planned event: {e}")
        raise ValidationError(f"Failed to delete planned event: {str(e)}")

    return success_response(message=f"Planned event '{event_id}' deleted successfully")
