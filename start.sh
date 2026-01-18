#!/bin/bash

# DataLens Startup Script
# Builds and starts both backend and frontend

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"
FRONTEND_DIR="$SCRIPT_DIR/frontend"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_step() {
    echo -e "${BLUE}==>${NC} $1"
}

print_success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# Check if .env file exists and source it
if [ -f "$SCRIPT_DIR/.env" ]; then
    print_step "Loading environment variables from .env"
    export $(grep -v '^#' "$SCRIPT_DIR/.env" | xargs)
    print_success "Environment variables loaded"
else
    print_warning ".env file not found. Make sure OPENAI_API_KEY is set."
    print_warning "Create a .env file with: OPENAI_API_KEY=your-key-here"
fi

# Check for required environment variable
if [ -z "$OPENAI_API_KEY" ]; then
    print_error "OPENAI_API_KEY is not set. Please set it in .env file or export it."
    exit 1
fi

# Kill any existing processes
print_step "Stopping any existing DataLens processes..."
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "vite.*datalens" 2>/dev/null || true
sleep 2

# Build and start backend
print_step "Building backend..."
cd "$BACKEND_DIR"
./mvnw clean compile -q
print_success "Backend compiled"

print_step "Starting backend server..."
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,mock,openai > /tmp/datalens-backend.log 2>&1 &
BACKEND_PID=$!
echo $BACKEND_PID > /tmp/datalens-backend.pid

# Wait for backend to be ready
print_step "Waiting for backend to start..."
for i in {1..30}; do
    if curl -s http://localhost:8080/api/v1/health > /dev/null 2>&1; then
        print_success "Backend is running on http://localhost:8080"
        break
    fi
    if [ $i -eq 30 ]; then
        print_error "Backend failed to start. Check logs: tail -f /tmp/datalens-backend.log"
        exit 1
    fi
    sleep 1
done

# Build and start frontend
print_step "Installing frontend dependencies..."
cd "$FRONTEND_DIR"
npm install --silent
print_success "Frontend dependencies installed"

print_step "Starting frontend server..."
npm run dev > /tmp/datalens-frontend.log 2>&1 &
FRONTEND_PID=$!
echo $FRONTEND_PID > /tmp/datalens-frontend.pid

# Wait for frontend to be ready
print_step "Waiting for frontend to start..."
for i in {1..15}; do
    if curl -s http://localhost:5173 > /dev/null 2>&1; then
        print_success "Frontend is running on http://localhost:5173"
        break
    fi
    if [ $i -eq 15 ]; then
        print_error "Frontend failed to start. Check logs: tail -f /tmp/datalens-frontend.log"
        exit 1
    fi
    sleep 1
done

# Open browser
print_step "Opening browser..."
if command -v open &> /dev/null; then
    open http://localhost:5173
elif command -v xdg-open &> /dev/null; then
    xdg-open http://localhost:5173
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  DataLens is running!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "  Frontend:  http://localhost:5173"
echo "  Backend:   http://localhost:8080"
echo "  Dashboard: http://localhost:8080/actuator/dashboard"
echo ""
echo "  Logs:"
echo "    Backend:  tail -f /tmp/datalens-backend.log"
echo "    Frontend: tail -f /tmp/datalens-frontend.log"
echo ""
echo "  To stop: ./stop.sh"
echo ""
