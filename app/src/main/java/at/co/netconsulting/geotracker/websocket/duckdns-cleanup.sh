#!/bin/sh
# DuckDNS Cleanup Hook for Certbot
# This script is called by certbot to remove the TXT record after validation

# Load DuckDNS token from config file
. /etc/duckdns.ini

# Extract domain name (remove .duckdns.org if present)
DOMAIN=$(echo "$CERTBOT_DOMAIN" | sed 's/\.duckdns\.org$//')

# Clear the TXT record using DuckDNS API
echo "Clearing TXT record for $DOMAIN"
RESPONSE=$(wget -qO- "https://www.duckdns.org/update?domains=${DOMAIN}&token=${DUCKDNS_TOKEN}&txt=&clear=true")

if [ "$RESPONSE" = "OK" ]; then
    echo "TXT record cleared successfully"
    exit 0
else
    echo "Failed to clear TXT record. Response: $RESPONSE"
    # Don't fail the renewal just because cleanup failed
    exit 0
fi
