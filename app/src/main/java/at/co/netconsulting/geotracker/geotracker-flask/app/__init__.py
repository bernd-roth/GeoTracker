import os
import logging
from flask import Flask
from .config import config
from .extensions import db


def create_app(config_name=None):
    """Application factory for creating Flask app instances."""

    if config_name is None:
        config_name = os.environ.get('FLASK_ENV', 'production')

    app = Flask(__name__)

    # Load configuration
    config_class = config.get(config_name, config['default'])
    app.config.from_object(config_class())

    # Configure logging
    configure_logging(app)

    # Initialize extensions
    db.init_app(app)

    # Register blueprints
    register_blueprints(app)

    # Register error handlers
    register_error_handlers(app)

    app.logger.info(f"Flask app created with config: {config_name}")

    return app


def configure_logging(app):
    """Configure application logging."""
    log_level = logging.DEBUG if app.debug else logging.INFO

    # Configure root logger
    logging.basicConfig(
        level=log_level,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )

    # Set Flask app logger level
    app.logger.setLevel(log_level)


def register_blueprints(app):
    """Register Flask blueprints."""
    from .api import api_bp
    app.register_blueprint(api_bp, url_prefix='/api')


def register_error_handlers(app):
    """Register error handlers."""
    from .api.errors import register_error_handlers as register_api_errors
    register_api_errors(app)
