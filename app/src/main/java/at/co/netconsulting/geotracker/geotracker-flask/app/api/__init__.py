from flask import Blueprint

api_bp = Blueprint('api', __name__)

# Import and register blueprints
from .health import health_bp
from .sessions import sessions_bp
from .planned_events import planned_events_bp
from .media import media_bp
from .analysis import analysis_bp
from .heatmap import heatmap_bp

api_bp.register_blueprint(health_bp)
api_bp.register_blueprint(sessions_bp)
api_bp.register_blueprint(planned_events_bp)
api_bp.register_blueprint(media_bp)
api_bp.register_blueprint(analysis_bp)
api_bp.register_blueprint(heatmap_bp)
