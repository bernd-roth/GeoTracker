#!/bin/bash
cd /home/bernd/docker-container/geotracker/

# Renew certificates
docker-compose run --rm certbot renew --quiet

# Fix permissions (in case they get reset)
sudo chown -R bernd:bernd ssl/
sudo chmod -R 755 ssl/

# Reload nginx to use new certificates
docker-compose exec nginx nginx -s reload

echo "Certificate renewal completed at $(date)"
