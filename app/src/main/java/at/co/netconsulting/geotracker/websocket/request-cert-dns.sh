#!/bin/bash
# Request a new certificate using DNS-01 challenge with DuckDNS
cd /home/bernd/docker-container/geotracker/

# Make scripts executable
chmod +x duckdns-auth.sh duckdns-cleanup.sh

# Request new certificate using manual DNS-01 challenge
# Note: NOT deleting old cert first, so nginx can keep running with old cert if this fails
echo "Requesting new certificate using DNS-01 challenge..."
docker-compose run --rm certbot certonly \
  --manual \
  --preferred-challenges=dns \
  --manual-auth-hook /scripts/duckdns-auth.sh \
  --manual-cleanup-hook /scripts/duckdns-cleanup.sh \
  --email YOUR_EMAIL@example.com \
  --agree-tos \
  --no-eff-email \
  --force-renewal \
  -d geotracker.duckdns.org

if [ $? -eq 0 ]; then
    echo "Certificate obtained successfully!"

    # Fix permissions
    sudo chown -R bernd:bernd ssl/
    sudo chmod -R 755 ssl/

    # Reload nginx to use new certificate
    if docker-compose exec nginx nginx -s reload; then
        echo "Nginx reloaded successfully"
    else
        echo "Failed to reload nginx, trying restart..."
        docker-compose restart nginx
    fi

    echo "Certificate request completed successfully at $(date)"
else
    echo "ERROR: Certificate request failed at $(date)"
    exit 1
fi
