# DataLens

AI-powered database query agent with Spring AI backend and React frontend. Interact with databases using natural language to explore schemas, execute queries, and analyze data.

## Features

- Natural Language Queries: Ask questions about your data in plain English
- Multi-Environment Support: Switch between DEV, UAT, and PROD environments
- Dual LLM Support: Azure OpenAI (certificate auth) or OpenAI (API key)
- Real-time Streaming: See responses as they're generated
- Tool Visualization: View database tool calls and their results
- Mock Mode: Test locally without database connectivity
- Telemetry Dashboard: Monitor requests, performance, and usage
- Dark/Light Mode: Toggle between themes

## Prerequisites

- Java 21+
- Node.js 18+
- Maven 3.9+
- OpenAI API key (or Azure OpenAI credentials)

## Quick Start

### 1. Clone and Configure

```bash
git clone https://github.com/yourusername/datalens.git
cd datalens
```

Create a `.env` file in the root directory:
```bash
OPENAI_API_KEY=your-openai-api-key-here
```

### 2. Start the Application

Run the startup script to build and start everything:
```bash
./start.sh
```

This will:
- Build the backend
- Build the frontend
- Start both services
- Open the application in your browser

### 3. Access the Application

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- Telemetry Dashboard: http://localhost:8080/actuator/dashboard

### 4. Stop the Application

```bash
./stop.sh
```

## Manual Setup

### Starting the Backend Only

```bash
cd backend

# Set the OpenAI API key
export OPENAI_API_KEY=your-key-here

# Run with default profiles (dev, mock, openai)
./mvnw spring-boot:run

# Or specify profiles explicitly
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,mock,openai
```

The backend starts on http://localhost:8080

### Starting the Frontend Only

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev
```

The frontend starts on http://localhost:5173

### Building for Production

Backend:
```bash
cd backend
./mvnw clean package -DskipTests
java -jar target/datalens-1.0.0.jar
```

Frontend:
```bash
cd frontend
npm run build
# Serve the dist folder with any static file server
```

## Viewing Logs

### Backend Logs

When running via startup script:
```bash
tail -f /tmp/datalens-backend.log
```

Filter for errors:
```bash
grep -i error /tmp/datalens-backend.log
```

Enable debug logging:
```bash
export LOG_LEVEL=DEBUG
```

### Frontend Logs

When running via startup script:
```bash
tail -f /tmp/datalens-frontend.log
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| OPENAI_API_KEY | OpenAI API key | Required |
| SERVER_PORT | Backend server port | 8080 |
| LLM_PROVIDER | LLM provider (openai/azure) | openai |
| DATASOURCE_MODE | Data source mode (mock/oracle) | mock |
| DEFAULT_SCHEMA | Default database schema | SCHEMA_A |
| LOG_LEVEL | Application log level | INFO |

### Spring Profiles

| Profile | Purpose |
|---------|---------|
| dev | Local development settings |
| mock | Use mock data source |
| openai | Use OpenAI API |
| azure | Use Azure OpenAI |
| oracle | Use Oracle database |

Profile combinations:
- Local dev: `dev,mock,openai`
- VDI with Azure: `azure,oracle`

## Project Structure

```
datalens/
├── backend/                     # Spring Boot application
│   ├── src/main/java/io/datalens/
│   │   ├── config/             # Configuration classes
│   │   ├── datasource/         # Data source implementations
│   │   ├── tools/              # LLM tools (database operations)
│   │   ├── service/            # Business logic
│   │   ├── telemetry/          # Metrics and monitoring
│   │   └── controller/         # REST endpoints
│   └── src/main/resources/
│       ├── application*.yml    # Configuration profiles
│       └── prompts/            # System prompts
├── frontend/                    # React application
│   └── src/
│       ├── components/         # UI components
│       ├── contexts/           # React contexts (theme)
│       └── hooks/              # Custom React hooks
├── start.sh                    # Start both services
├── stop.sh                     # Stop both services
└── .env                        # Environment variables (not in git)
```

## Available Tools

The AI assistant has access to these database tools:

| Tool | Description |
|------|-------------|
| executeQuery | Execute SQL SELECT queries |
| connectToEnvironment | Switch between DEV/UAT/PROD |
| getCurrentStatus | Check connection status |
| getTableSchema | Get table structure |
| listTables | List available tables |

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| /api/v1/chat/stream | POST | Streaming chat with tool calls |
| /api/v1/chat | POST | Simple streaming chat |
| /api/v1/chat/sync | POST | Non-streaming chat |
| /api/v1/health | GET | Health check |
| /actuator/dashboard | GET | Telemetry dashboard |
| /actuator/metrics | GET | Raw metrics |

## Telemetry Dashboard

Access the metrics dashboard at http://localhost:8080/actuator/dashboard

Tracked metrics:
- Active sessions
- Request counts by environment and tool
- Query execution times
- Error counts
- JVM memory usage

## Troubleshooting

### Backend fails to start

1. Check if port 8080 is in use:
   ```bash
   lsof -i :8080
   ```

2. Verify OPENAI_API_KEY is set:
   ```bash
   echo $OPENAI_API_KEY
   ```

3. Check backend logs:
   ```bash
   tail -100 /tmp/datalens-backend.log
   ```

### Frontend fails to start

1. Check if port 5173 is in use:
   ```bash
   lsof -i :5173
   ```

2. Clear node modules and reinstall:
   ```bash
   cd frontend
   rm -rf node_modules
   npm install
   ```

### Chat not responding

1. Verify backend is running:
   ```bash
   curl http://localhost:8080/api/v1/health
   ```

2. Check for errors in logs:
   ```bash
   grep -i error /tmp/datalens-backend.log
   ```

## License

MIT
