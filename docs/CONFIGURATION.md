# DataLens Configuration Guide

This document covers all configuration options for DataLens.

## Profile System

DataLens uses Spring Boot profiles for environment-specific configuration. Profiles are additive and can be combined.

### Available Profiles

| Profile | Purpose |
|---------|---------|
| `dev` | Development settings with debug logging |
| `mock` | Use MockDataSource instead of Oracle |
| `openai` | Use OpenAI with API key authentication |
| `azure` | Use Azure OpenAI with certificate authentication |
| `oracle` | Use Oracle database with Kerberos |

### Activating Profiles

```bash
# Environment variable (recommended)
export SPRING_PROFILES_ACTIVE=azure,oracle

# Command line
java -jar datalens.jar --spring.profiles.active=azure,oracle

# In application.yml (default)
spring:
  profiles:
    active: dev,mock,openai
```

### Common Profile Combinations

| Use Case | Profiles |
|----------|----------|
| Local development (no DB) | `dev,mock,openai` |
| VDI with Azure OpenAI | `azure,oracle` |
| VDI with OpenAI | `openai,oracle` |
| Testing against UAT | `openai,oracle` (set `DEFAULT_ENV=uat`) |

## OpenAI Configuration

### Standard OpenAI (API Key)

```yaml
# application-openai.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4
          temperature: 0.7
          max-tokens: 4096
```

**Environment Variables:**
```bash
export OPENAI_API_KEY=sk-...
export OPENAI_MODEL=gpt-4  # optional, defaults to gpt-4
```

## Azure OpenAI Configuration

### Certificate Authentication

Azure OpenAI uses certificate authentication instead of API keys for enterprise deployments.

```yaml
# application-azure.yml
azure:
  openai:
    endpoint: ${AZURE_OPENAI_ENDPOINT}
    tenant-id: ${AZURE_TENANT_ID}
    client-id: ${AZURE_CLIENT_ID}
    certificate-path: ${AZURE_CERT_PATH}
    deployment-name: ${AZURE_DEPLOYMENT_NAME:gpt-4}

spring:
  ai:
    azure:
      openai:
        chat:
          options:
            deployment-name: ${AZURE_DEPLOYMENT_NAME:gpt-4}
            temperature: 0.7
            max-tokens: 4096
```

**Environment Variables:**
```bash
export AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com
export AZURE_TENANT_ID=your-tenant-id
export AZURE_CLIENT_ID=your-app-client-id
export AZURE_CERT_PATH=/path/to/certificate.pem
export AZURE_DEPLOYMENT_NAME=gpt-4
```

**Important Notes:**
- Do NOT set `api-key` when using certificate auth
- The certificate should be a PEM file containing both the certificate and private key
- Ensure the app registration has the correct API permissions

## Oracle Database Configuration

### Kerberos Authentication

DataLens uses Kerberos for Oracle authentication - no username/password needed.

```yaml
# application-oracle.yml
datalens:
  datasource:
    mode: oracle
  oracle:
    kerberos:
      config-path: ${KRB5_CONF_PATH:/etc/krb5.conf}
      ccache-path: ${KRB5_CCACHE}
    query-timeout-seconds: 60
    environments:
      dev:
        url: ${DEV_DB_URL}
      uat:
        url: ${UAT_DB_URL}
      prod:
        url: ${PROD_DB_URL}
```

**Environment Variables:**
```bash
# Kerberos
export KRB5_CONF_PATH=/etc/krb5.conf
export KRB5_CCACHE=/tmp/krb5cc_youruser

# Database URLs
export DEV_DB_URL=jdbc:oracle:thin:@//dev-host:1521/devdb
export UAT_DB_URL=jdbc:oracle:thin:@//uat-host:1521/uatdb
export PROD_DB_URL=jdbc:oracle:thin:@//prod-host:1521/proddb
```

**Prerequisites:**
1. Run `kinit` to obtain a Kerberos ticket before starting the application
2. Ensure `krb5.conf` is properly configured for your realm
3. The JDBC URL should use TNS or Easy Connect format

### Connection Properties

The following Oracle JDBC properties are automatically set:

| Property | Value | Purpose |
|----------|-------|---------|
| `oracle.net.authentication_services` | `(KERBEROS5)` | Enable Kerberos |
| `oracle.net.kerberos5_cc_name` | Credential cache path | Ticket location |
| `oracle.net.CONNECT_TIMEOUT` | 60000ms | Connection timeout |
| `oracle.jdbc.ReadTimeout` | 60000ms | Read timeout |

## Schema Configuration

```yaml
datalens:
  schema:
    default: ${DEFAULT_SCHEMA:SCHEMA_A}
    secondary: ${SECONDARY_SCHEMA:SCHEMA_B}
```

These placeholders are replaced in the system prompt:
- `{{defaultSchema}}` → Primary schema name
- `{{secondarySchema}}` → Secondary schema name

## Chat Memory Configuration

In-memory chat history with JVM protection:

```yaml
datalens:
  memory:
    max-messages-per-session: 20   # Messages kept per conversation
    max-sessions: 1000             # Max concurrent sessions
    session-timeout-minutes: 30    # Inactive session cleanup
```

**Memory Estimation:**
- ~1KB per message average
- 20 messages × 1000 sessions = ~20 MB max

## CORS Configuration

```yaml
datalens:
  cors:
    allowed-origins: http://localhost:3000,http://localhost:5173
```

For production, update to your frontend domain.

## Actuator & Monitoring

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,dashboard
  endpoint:
    health:
      show-details: when_authorized
```

**Endpoints:**
- `/actuator/health` - Health check
- `/actuator/metrics` - All available metrics
- `/actuator/metrics/{name}` - Specific metric details
- `/actuator/dashboard` - Custom metrics dashboard

## Logging

```yaml
logging:
  level:
    io.datalens: INFO           # Application logs
    org.springframework.ai: INFO # Spring AI logs
    org.springframework.web: INFO # Web layer logs
```

For debugging, set to `DEBUG`:
```bash
export LOG_LEVEL=DEBUG
```

## System Prompt

The system prompt can be customized:

```yaml
datalens:
  prompt:
    file: ${SYSTEM_PROMPT_FILE:classpath:prompts/system-prompt.txt}
```

To use an external file:
```bash
export SYSTEM_PROMPT_FILE=file:/path/to/custom-prompt.txt
```

## Complete Environment Variable Reference

### Core Settings
| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 8080 | HTTP port |
| `LLM_PROVIDER` | openai | azure or openai |
| `DATASOURCE_MODE` | mock | oracle or mock |
| `LOG_LEVEL` | INFO | Logging level |

### OpenAI
| Variable | Required | Description |
|----------|----------|-------------|
| `OPENAI_API_KEY` | Yes | API key |
| `OPENAI_MODEL` | No | Model name (default: gpt-4) |

### Azure OpenAI
| Variable | Required | Description |
|----------|----------|-------------|
| `AZURE_OPENAI_ENDPOINT` | Yes | Endpoint URL |
| `AZURE_TENANT_ID` | Yes | Azure AD tenant |
| `AZURE_CLIENT_ID` | Yes | App registration client ID |
| `AZURE_CERT_PATH` | Yes | Path to PEM certificate |
| `AZURE_DEPLOYMENT_NAME` | No | Deployment name (default: gpt-4) |

### Oracle
| Variable | Required | Description |
|----------|----------|-------------|
| `DEV_DB_URL` | Yes | DEV environment JDBC URL |
| `UAT_DB_URL` | Yes | UAT environment JDBC URL |
| `PROD_DB_URL` | Yes | PROD environment JDBC URL |
| `KRB5_CONF_PATH` | No | krb5.conf path (default: /etc/krb5.conf) |
| `KRB5_CCACHE` | No | Credential cache path |
| `QUERY_TIMEOUT` | No | Query timeout seconds (default: 60) |

### Schema
| Variable | Default | Description |
|----------|---------|-------------|
| `DEFAULT_SCHEMA` | SCHEMA_A | Primary schema name |
| `SECONDARY_SCHEMA` | SCHEMA_B | Secondary schema name |

### Memory
| Variable | Default | Description |
|----------|---------|-------------|
| `MAX_MESSAGES_PER_SESSION` | 20 | Messages per conversation |
| `MAX_SESSIONS` | 1000 | Maximum concurrent sessions |
| `SESSION_TIMEOUT_MINUTES` | 30 | Inactive session timeout |
