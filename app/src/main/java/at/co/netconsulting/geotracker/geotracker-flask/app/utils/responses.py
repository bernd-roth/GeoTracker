from flask import jsonify


def success_response(data=None, message=None, status_code=200):
    """Create a standardized success response."""
    response = {'success': True}

    if message:
        response['message'] = message

    if data is not None:
        response['data'] = data

    return jsonify(response), status_code


def error_response(message, status_code=400, errors=None):
    """Create a standardized error response."""
    response = {
        'success': False,
        'error': message
    }

    if errors:
        response['errors'] = errors

    return jsonify(response), status_code


def paginated_response(items, page, per_page, total, item_name='items'):
    """Create a standardized paginated response."""
    total_pages = (total + per_page - 1) // per_page

    return jsonify({
        'success': True,
        'data': {
            item_name: items,
            'pagination': {
                'page': page,
                'per_page': per_page,
                'total': total,
                'total_pages': total_pages,
                'has_next': page < total_pages,
                'has_prev': page > 1
            }
        }
    })
