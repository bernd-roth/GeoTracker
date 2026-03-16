import re
from flask import Blueprint, request
from sqlalchemy import func
from ..extensions import db
from ..models import TrackingSession, GPSTrackingPoint, LapTime
from ..utils.responses import success_response
from .errors import NotFoundError

analysis_bp = Blueprint('analysis', __name__)

# ─────────────────────────────────────────────────────────────
# HELPERS
# ─────────────────────────────────────────────────────────────

def _base_id(session_id):
    """Strip _reset_<digits> suffix to get the original session ID."""
    return re.sub(r'_reset_\d+$', '', session_id)


def _escape_like(value):
    """Escape LIKE special characters (% _ \\) so they are treated literally."""
    return value.replace('\\', '\\\\').replace('%', '\\%').replace('_', '\\_')


def _all_session_ids(base):
    """Return [base_id, ...all reset variants] for a given base session ID.

    Uses an escaped LIKE pattern so that underscores in the base ID are
    treated as literal characters, not as single-character SQL wildcards.
    """
    escaped = _escape_like(base)
    rows = (
        db.session.query(TrackingSession.session_id)
        .filter(
            db.or_(
                TrackingSession.session_id == base,
                TrackingSession.session_id.like(escaped + '\\_reset\\_%', escape='\\')
            )
        )
        .all()
    )
    return [r[0] for r in rows]


def _m_to_km(meters):
    """Convert metres to kilometres, return None for None/zero."""
    if meters is None:
        return None
    v = float(meters)
    return round(v / 1000.0, 3) if v else None


# ─────────────────────────────────────────────────────────────
# TRACK
# ─────────────────────────────────────────────────────────────

@analysis_bp.route('/sessions/<session_id>/track', methods=['GET'])
def get_track(session_id):
    """Return GPS track points in a lean format for map rendering.

    Query params:
        max_points:      Optional int. Downsample to ~N points (0 = all).
        include_resets:  true → merge GPS points from all _reset_ variants.

    Distance in the database is stored in metres and is cumulative across the
    entire activity (it does not reset to 0 when a new _reset_ session starts).
    The returned `distance` field is therefore converted to km and requires no
    artificial offset.
    """
    session = TrackingSession.query.get(session_id)
    if not session:
        raise NotFoundError(f"Session '{session_id}' not found")

    include_resets = request.args.get('include_resets', 'false').lower() == 'true'
    max_points     = request.args.get('max_points', 0, type=int)

    if include_resets:
        base        = _base_id(session_id)
        session_ids = _all_session_ids(base)
    else:
        session_ids = [session_id]

    points = (
        GPSTrackingPoint.query
        .filter(GPSTrackingPoint.session_id.in_(session_ids))
        .order_by(GPSTrackingPoint.received_at)
        .all()
    )

    if max_points > 0 and len(points) > max_points:
        step    = len(points) / max_points
        indices = {int(i * step) for i in range(max_points)}
        indices.add(len(points) - 1)
        points = [p for i, p in enumerate(points) if i in indices]

    track = [
        {
            'lat':      float(p.latitude),
            'lon':      float(p.longitude),
            'alt':      float(p.altitude)                  if p.altitude                  is not None else None,
            'speed':    float(p.current_speed)             if p.current_speed             is not None else None,
            'hr':       p.heart_rate,
            # distance is stored in metres → convert to km for chart axis
            'distance': _m_to_km(p.distance),
            'ts':       p.received_at.isoformat()          if p.received_at               else None,
            'lap':      p.lap,
            'slope':    float(p.slope)                     if p.slope                     is not None else None,
            'ele_gain': float(p.cumulative_elevation_gain) if p.cumulative_elevation_gain is not None else None,
        }
        for p in points
    ]

    return success_response(data={'session_id': session_id, 'points': track})


# ─────────────────────────────────────────────────────────────
# LAPS
# ─────────────────────────────────────────────────────────────

@analysis_bp.route('/sessions/<session_id>/laps', methods=['GET'])
def get_laps(session_id):
    """Return lap splits with computed pace.

    Query params:
        include_resets: true → merge laps from all _reset_ variants,
                        ordered by start_time and re-numbered sequentially.
    """
    session = TrackingSession.query.get(session_id)
    if not session:
        raise NotFoundError(f"Session '{session_id}' not found")

    include_resets = request.args.get('include_resets', 'false').lower() == 'true'

    if include_resets:
        base        = _base_id(session_id)
        session_ids = _all_session_ids(base)
    else:
        session_ids = [session_id]

    laps = (
        LapTime.query
        .filter(LapTime.session_id.in_(session_ids))
        .order_by(LapTime.start_time)
        .all()
    )

    result = []
    for i, lap in enumerate(laps, 1):
        duration_ms = lap.duration
        distance_km = float(lap.distance) if lap.distance else 0.0
        pace = round((duration_ms / 1000 / 60) / distance_km, 2) \
               if distance_km > 0 and duration_ms > 0 else None
        result.append({
            'lap_number':      i,
            'start_time':      lap.start_time,
            'end_time':        lap.end_time,
            'duration_ms':     duration_ms,
            'distance_km':     distance_km,
            'pace_min_per_km': pace,
        })

    return success_response(data={'session_id': session_id, 'laps': result})


# ─────────────────────────────────────────────────────────────
# SUMMARY
# ─────────────────────────────────────────────────────────────

@analysis_bp.route('/sessions/<session_id>/summary', methods=['GET'])
def get_summary(session_id):
    """Return aggregated stats for a session.

    Query params:
        include_resets: true → aggregate across base + all _reset_ variants.

    Distance and cumulative_elevation_gain are stored in metres and are
    cumulative across the ENTIRE activity — they do NOT reset when a new
    _reset_ session is created.  Therefore the correct totals are:

        total_distance_km  = MAX(distance across ALL fragments) / 1000
        elevation_gain_m   = MAX(cumulative_elevation_gain across ALL fragments)

    Duration is the sum of the individual fragment durations (gaps between
    fragments caused by reconnection are excluded).
    """
    session = TrackingSession.query.get(session_id)
    if not session:
        raise NotFoundError(f"Session '{session_id}' not found")

    include_resets = request.args.get('include_resets', 'false').lower() == 'true'

    if include_resets:
        base        = _base_id(session_id)
        session_ids = _all_session_ids(base)
    else:
        session_ids = [session_id]

    # ── Totals: MAX across all fragments (distance/ele_gain are continuous) ─
    totals = (
        db.session.query(
            func.max(GPSTrackingPoint.distance).label('max_distance'),
            func.max(GPSTrackingPoint.cumulative_elevation_gain).label('max_ele_gain'),
        )
        .filter(GPSTrackingPoint.session_id.in_(session_ids))
        .first()
    )

    # ── Duration: sum of per-fragment intervals (exclude reconnection gaps) ─
    per_fragment = (
        db.session.query(
            func.min(GPSTrackingPoint.received_at).label('frag_start'),
            func.max(GPSTrackingPoint.received_at).label('frag_end'),
        )
        .filter(GPSTrackingPoint.session_id.in_(session_ids))
        .group_by(GPSTrackingPoint.session_id)
        .all()
    )

    duration_ms = int(sum(
        (f.frag_end - f.frag_start).total_seconds() * 1000
        for f in per_fragment
        if f.frag_start and f.frag_end
    )) if per_fragment else None

    # ── Simple aggregates across all points ─────────────────────────────────
    agg = (
        db.session.query(
            func.max(GPSTrackingPoint.current_speed).label('max_speed'),
            func.avg(GPSTrackingPoint.current_speed).label('avg_speed'),
            func.avg(GPSTrackingPoint.heart_rate).label('avg_hr'),
            func.max(GPSTrackingPoint.heart_rate).label('max_hr'),
            func.count(GPSTrackingPoint.id).label('point_count'),
        )
        .filter(GPSTrackingPoint.session_id.in_(session_ids))
        .first()
    )

    lap_count     = LapTime.query.filter(LapTime.session_id.in_(session_ids)).count()
    variant_count = len(session_ids)

    summary = {
        'session_id':        session_id,
        'event_name':        session.event_name,
        'sport_type':        session.sport_type,
        'start_date_time':   session.start_date_time.isoformat() if session.start_date_time else None,
        'start_city':        session.start_city,
        'start_country':     session.start_country,
        # distance stored in metres → convert to km
        'total_distance_km': _m_to_km(totals.max_distance)        if totals and totals.max_distance  else None,
        'max_speed_kmh':     round(float(agg.max_speed),  2)      if agg.max_speed                   else None,
        'avg_speed_kmh':     round(float(agg.avg_speed),  2)      if agg.avg_speed                   else None,
        # elevation_gain stored in metres already
        'elevation_gain_m':  round(float(totals.max_ele_gain), 1) if totals and totals.max_ele_gain  else None,
        'avg_heart_rate':    round(float(agg.avg_hr),     1)      if agg.avg_hr                      else None,
        'max_heart_rate':    int(agg.max_hr)                      if agg.max_hr                      else None,
        'duration_ms':       duration_ms,
        'point_count':       agg.point_count,
        'lap_count':         lap_count,
        'variant_count':     variant_count,
    }

    return success_response(data=summary)
