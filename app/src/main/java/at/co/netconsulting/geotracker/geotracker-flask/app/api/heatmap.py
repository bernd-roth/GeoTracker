from flask import Blueprint, request
from sqlalchemy import func, extract
from ..extensions import db
from ..models import TrackingSession, GPSTrackingPoint
from ..utils.responses import success_response

heatmap_bp = Blueprint('heatmap', __name__)


@heatmap_bp.route('/heatmap', methods=['GET'])
def get_heatmap_data():
    """Return compact GPS coordinates for heatmap rendering.

    Query params:
        sport:      Optional. Filter by sport_type.
        year:       Optional int. Filter by year of start_date_time.
        max_points: Optional int (default 50000). Downsample if more points.
    """
    sport      = request.args.get('sport', '').strip() or None
    year       = request.args.get('year', None, type=int)
    max_points = request.args.get('max_points', 50000, type=int)

    # Build a subquery for matching session IDs
    sess_q = db.session.query(TrackingSession.session_id)

    if sport:
        sess_q = sess_q.filter(TrackingSession.sport_type == sport)
    if year:
        sess_q = sess_q.filter(
            extract('year', TrackingSession.start_date_time) == year
        )

    session_ids_sub = sess_q.subquery()

    # Count total points
    total = (
        db.session.query(func.count(GPSTrackingPoint.id))
        .filter(GPSTrackingPoint.session_id.in_(
            db.session.query(session_ids_sub.c.session_id)
        ))
        .scalar()
    ) or 0

    # Compute sample step
    step = max(1, total // max_points) if max_points > 0 else 1

    # Fetch only lat/lon with modulo-based downsampling
    rows = (
        db.session.query(
            GPSTrackingPoint.latitude,
            GPSTrackingPoint.longitude,
        )
        .filter(GPSTrackingPoint.session_id.in_(
            db.session.query(session_ids_sub.c.session_id)
        ))
    )

    if step > 1:
        rows = rows.filter(GPSTrackingPoint.id % step == 0)

    rows = rows.all()

    points = [[float(r.latitude), float(r.longitude)] for r in rows]

    return success_response(data={
        'points': points,
        'total': total,
        'returned': len(points),
    })


@heatmap_bp.route('/heatmap/filters', methods=['GET'])
def get_heatmap_filters():
    """Return available sport types and years for the heatmap filter controls."""
    sports = (
        db.session.query(TrackingSession.sport_type)
        .filter(TrackingSession.sport_type.isnot(None))
        .distinct()
        .order_by(TrackingSession.sport_type)
        .all()
    )

    years = (
        db.session.query(
            extract('year', TrackingSession.start_date_time).label('y')
        )
        .filter(TrackingSession.start_date_time.isnot(None))
        .distinct()
        .order_by(extract('year', TrackingSession.start_date_time).desc())
        .all()
    )

    return success_response(data={
        'sports': [s[0] for s in sports if s[0]],
        'years':  [int(y[0]) for y in years if y[0]],
    })
