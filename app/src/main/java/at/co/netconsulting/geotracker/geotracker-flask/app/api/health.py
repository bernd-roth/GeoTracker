from flask import Blueprint, current_app
from sqlalchemy import text
from ..extensions import db
from ..utils.responses import success_response, error_response

health_bp = Blueprint('health', __name__)


@health_bp.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint."""
    health_status = {
        'status': 'healthy',
        'service': 'geotracker-flask-api',
        'database': 'unknown'
    }

    # Check database connection
    try:
        db.session.execute(text('SELECT 1'))
        health_status['database'] = 'connected'
    except Exception as e:
        current_app.logger.error(f"Database health check failed: {e}")
        health_status['database'] = 'disconnected'
        health_status['status'] = 'unhealthy'
        return error_response(
            message='Service unhealthy',
            status_code=503
        )

    return success_response(data=health_status)
