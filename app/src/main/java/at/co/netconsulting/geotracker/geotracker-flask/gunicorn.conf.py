# Gunicorn configuration file

# Server socket
bind = "0.0.0.0:5000"

# Worker processes
workers = 2
worker_class = "sync"
threads = 4

# Timeouts
timeout = 120
keepalive = 5

# Logging
accesslog = "/app/logs/access.log"
errorlog = "/app/logs/error.log"
loglevel = "info"
capture_output = True

# Process naming
proc_name = "geotracker-flask-api"

# Server mechanics
daemon = False
pidfile = None
umask = 0
user = None
group = None
tmp_upload_dir = None

# Restart workers after this many requests
max_requests = 1000
max_requests_jitter = 100

# Graceful timeout
graceful_timeout = 30
