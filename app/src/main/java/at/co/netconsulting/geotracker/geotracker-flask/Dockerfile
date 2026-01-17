FROM arm64v8/python:3.9-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    libpq-dev \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for better caching
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app/ ./app/
COPY gunicorn.conf.py .

# Create logs directory
RUN mkdir -p /app/logs

# Expose port
EXPOSE 5000

# Run with gunicorn
CMD ["gunicorn", "--config", "gunicorn.conf.py", "app:create_app()"]
