from flask import Blueprint

api_bp = Blueprint('api', __name__)

# Import and register blueprints
from .health import health_bp
from .sessions import sessions_bp
from .planned_events import planned_events_bp

api_bp.register_blueprint(health_bp)
api_bp.register_blueprint(sessions_bp)
api_bp.register_blueprint(planned_events_bp)
