#!/bin/bash

# Docker Setup Script for GeoTracker with PostgreSQL

echo "🚀 Setting up GeoTracker with PostgreSQL in Docker"
echo "="*50

# Step 1: Stop existing containers
echo "📦 Stopping existing containers..."
cd /home/bernd/docker-container/geotracker
docker-compose down

cd /home/bernd/docker-container/postgres-geotracker
docker-compose down

# Step 2: Create the external network first (if it doesn't exist)
echo "🌐 Creating shared network..."
docker network create geotracker-network 2>/dev/null || echo "Network already exists"

# Step 3: Start PostgreSQL first
echo "🐘 Starting PostgreSQL..."
cd /home/bernd/docker-container/postgres-geotracker
docker-compose up -d

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL to be ready..."
sleep 10

# Test PostgreSQL connection
echo "🔍 Testing PostgreSQL connection..."
docker exec postgres-geotracker pg_isready -U geotracker -d geotracker

if [ $? -eq 0 ]; then
    echo "✅ PostgreSQL is ready!"
else
    echo "❌ PostgreSQL is not ready. Check logs:"
    docker logs postgres-geotracker
    exit 1
fi

# Step 4: Verify database table exists
echo "📋 Checking database table..."
TABLE_EXISTS=$(docker exec postgres-geotracker psql -U geotracker -d geotracker -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'tracking_data';" | tr -d ' \n')

if [ "$TABLE_EXISTS" = "1" ]; then
    echo "✅ tracking_data table exists!"
else
    echo "❌ tracking_data table missing! Check init script."
    exit 1
fi

# Step 5: Start websocket server
echo "🌐 Starting WebSocket server..."
cd /home/bernd/docker-container/geotracker
docker-compose up -d

# Wait for websocket server to start
echo "⏳ Waiting for WebSocket server to start..."
sleep 5

# Step 6: Check logs
echo "📋 WebSocket server logs:"
docker logs geotracker-websocket-server-1 --tail 20

echo ""
echo "🏁 Setup complete!"
echo ""
echo "🔍 Useful commands:"
echo "  Check PostgreSQL: docker logs postgres-geotracker"
echo "  Check WebSocket:   docker logs geotracker-websocket-server-1"
echo "  Database shell:    docker exec -it postgres-geotracker psql -U geotracker -d geotracker"
echo "  View data:         docker exec postgres-geotracker psql -U geotracker -d geotracker -c 'SELECT COUNT(*) FROM tracking_data;'"
echo ""
echo "🌐 Services:"
echo "  Website:     http://localhost:8011"
echo "  PostgreSQL:  localhost:8021"
echo "  WebSocket:   ws://localhost:6789"
