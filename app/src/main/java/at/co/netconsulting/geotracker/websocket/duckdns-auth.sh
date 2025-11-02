#!/bin/sh
# DuckDNS Authentication Hook for Certbot
# This script is called by certbot to create a TXT record for DNS-01 validation

# Load DuckDNS token from config file
. /etc/duckdns.ini

# Extract domain name (remove .duckdns.org if present)
DOMAIN=$(echo "$CERTBOT_DOMAIN" | sed 's/\.duckdns\.org$//')

# Set the TXT record using DuckDNS API
# DuckDNS only supports one TXT record, so we set it directly
echo "Setting TXT record for $DOMAIN with value: $CERTBOT_VALIDATION"
RESPONSE=$(wget -qO- "https://www.duckdns.org/update?domains=${DOMAIN}&token=${DUCKDNS_TOKEN}&txt=${CERTBOT_VALIDATION}")

if [ "$RESPONSE" = "OK" ]; then
    echo "TXT record set successfully"
    # Wait for DNS propagation (DuckDNS is usually fast, but be safe)
    echo "Waiting 30 seconds for DNS propagation..."
    sleep 30
    exit 0
else
    echo "Failed to set TXT record. Response: $RESPONSE"
    exit 1
fi
