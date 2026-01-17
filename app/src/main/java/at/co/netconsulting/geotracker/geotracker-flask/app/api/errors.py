from flask import jsonify
from sqlalchemy.exc import SQLAlchemyError


class APIError(Exception):
    """Base API exception."""

    def __init__(self, message, status_code=400, errors=None):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.errors = errors


class NotFoundError(APIError):
    """Resource not found error."""

    def __init__(self, message="Resource not found"):
        super().__init__(message, status_code=404)


class ValidationError(APIError):
    """Validation error."""

    def __init__(self, message="Validation failed", errors=None):
        super().__init__(message, status_code=400, errors=errors)


class ConflictError(APIError):
    """Resource conflict error."""

    def __init__(self, message="Resource already exists"):
        super().__init__(message, status_code=409)


def register_error_handlers(app):
    """Register error handlers with the Flask app."""

    @app.errorhandler(APIError)
    def handle_api_error(error):
        response = {
            'success': False,
            'error': error.message
        }
        if error.errors:
            response['errors'] = error.errors
        return jsonify(response), error.status_code

    @app.errorhandler(404)
    def handle_not_found(error):
        return jsonify({
            'success': False,
            'error': 'Resource not found'
        }), 404

    @app.errorhandler(405)
    def handle_method_not_allowed(error):
        return jsonify({
            'success': False,
            'error': 'Method not allowed'
        }), 405

    @app.errorhandler(500)
    def handle_internal_error(error):
        app.logger.error(f"Internal server error: {error}")
        return jsonify({
            'success': False,
            'error': 'Internal server error'
        }), 500

    @app.errorhandler(SQLAlchemyError)
    def handle_db_error(error):
        app.logger.error(f"Database error: {error}")
        return jsonify({
            'success': False,
            'error': 'Database error occurred'
        }), 500
