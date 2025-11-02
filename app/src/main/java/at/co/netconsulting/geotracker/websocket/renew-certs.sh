#!/bin/bash
cd /home/bernd/docker-container/geotracker/

# Log file for debugging
LOG_FILE="/home/bernd/docker-container/geotracker/logs/certbot-renewal.log"

echo "Starting certificate renewal at $(date)" | tee -a "$LOG_FILE"

# Make scripts executable
chmod +x duckdns-auth.sh duckdns-cleanup.sh

# Renew certificates using DNS-01 challenge
# Certbot will automatically use the hooks from the previous certificate request
if docker-compose run --rm certbot renew --manual-auth-hook /scripts/duckdns-auth.sh --manual-cleanup-hook /scripts/duckdns-cleanup.sh 2>&1 | tee -a "$LOG_FILE"; then
    echo "Certificate renewal successful" | tee -a "$LOG_FILE"

    # Fix permissions (in case they get reset)
    sudo chown -R bernd:bernd ssl/
    sudo chmod -R 755 ssl/

    # Reload nginx to use new certificates
    if docker-compose exec nginx nginx -s reload 2>&1 | tee -a "$LOG_FILE"; then
        echo "Nginx reloaded successfully" | tee -a "$LOG_FILE"
    else
        echo "ERROR: Failed to reload nginx" | tee -a "$LOG_FILE"
    fi
else
    echo "ERROR: Certificate renewal failed" | tee -a "$LOG_FILE"
fi

echo "Certificate renewal completed at $(date)" | tee -a "$LOG_FILE"
