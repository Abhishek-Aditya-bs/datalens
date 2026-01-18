#!/bin/bash

# DataLens Stop Script
# Stops both backend and frontend services

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

echo "Stopping DataLens services..."

# Stop backend
if [ -f /tmp/datalens-backend.pid ]; then
    PID=$(cat /tmp/datalens-backend.pid)
    if kill -0 $PID 2>/dev/null; then
        kill $PID 2>/dev/null
        echo -e "${GREEN}[OK]${NC} Backend stopped (PID: $PID)"
    fi
    rm -f /tmp/datalens-backend.pid
fi

# Stop frontend
if [ -f /tmp/datalens-frontend.pid ]; then
    PID=$(cat /tmp/datalens-frontend.pid)
    if kill -0 $PID 2>/dev/null; then
        kill $PID 2>/dev/null
        echo -e "${GREEN}[OK]${NC} Frontend stopped (PID: $PID)"
    fi
    rm -f /tmp/datalens-frontend.pid
fi

# Also kill any remaining processes
pkill -f "spring-boot:run.*datalens" 2>/dev/null || true
pkill -f "vite.*datalens" 2>/dev/null || true

echo "DataLens services stopped."
