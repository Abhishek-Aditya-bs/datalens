# DataLens Customization Guide

This guide covers how to customize DataLens for your organization.

## Package Renaming

To rebrand DataLens with your organization's package name:

### Using IntelliJ IDEA

1. Right-click on `io.datalens` package
2. Select **Refactor** → **Rename**
3. Enter new package name: `com.yourorg.dbagent`
4. Update `pom.xml`:
   ```xml
   <groupId>com.yourorg</groupId>
   <artifactId>dbagent</artifactId>
   ```

### Using VS Code

1. Use "Find and Replace" across all files
2. Replace `io.datalens` with `com.yourorg.dbagent`
3. Rename directory structure manually
4. Update `pom.xml` groupId and artifactId

### Files to Update

- All Java files in `backend/src/main/java/`
- `backend/pom.xml` (groupId, artifactId)
- Import statements in configuration classes

## System Prompt Customization

### Location

The system prompt is at:
```
backend/src/main/resources/prompts/system-prompt.txt
```

### Placeholders

These placeholders are automatically replaced:
- `{{defaultSchema}}` → Value of `datalens.schema.default`
- `{{secondarySchema}}` → Value of `datalens.schema.secondary`

### Example Customization

```text
You are DataLens, an intelligent database assistant for [Your Company].

## Your Role
Help users query and analyze data from our [Department] databases.

## Available Schemas
- Primary: {{defaultSchema}} - Contains customer and order data
- Secondary: {{secondarySchema}} - Contains product catalog

## Domain Knowledge
- CUSTOMERS table: Customer master data
- ORDERS table: Transaction history (FK to CUSTOMERS)
- PRODUCTS table: Product catalog with pricing

## Business Rules
- Always filter by ACTIVE_FLAG = 'Y' for active records
- Use TO_DATE for date comparisons
- Mask sensitive columns (SSN, CREDIT_CARD)

## Example Queries
"Show active customers" →
SELECT * FROM {{defaultSchema}}.CUSTOMERS
WHERE ACTIVE_FLAG = 'Y'
AND ROWNUM <= 10

[Rest of your customizations...]
```

### External Prompt File

To use an external file instead of bundled:

```bash
export SYSTEM_PROMPT_FILE=file:/path/to/custom-prompt.txt
```

## Removing Mock Data

For production deployment, remove the mock data source:

### Steps

1. **Delete the mock file**:
   ```bash
   rm backend/src/main/java/io/datalens/datasource/MockDataSource.java
   ```

2. **Update application.yml** - remove `mock` from default profiles:
   ```yaml
   spring:
     profiles:
       active: ${SPRING_PROFILES_ACTIVE:oracle,openai}
   ```

3. **Delete dev profile** (optional):
   ```bash
   rm backend/src/main/resources/application-dev.yml
   ```

### GitHub Copilot Prompt

If using Copilot, ask:
> "Remove all mock data source code from this project, keeping only the Oracle implementation"

## Adding Custom Schemas

### In Configuration

```yaml
datalens:
  schema:
    default: YOUR_PRIMARY_SCHEMA
    secondary: YOUR_SECONDARY_SCHEMA
    # Add more schemas as needed
    analytics: ANALYTICS_SCHEMA
```

### In DatabaseTools.java

Add a parameter for schema selection:

```java
@Tool(description = "Execute SQL SELECT query...")
public String executeQuery(
    @ToolParam(description = "SQL query") String sql,
    @ToolParam(description = "Target schema (optional)") String schema
) {
    String targetSchema = (schema != null) ? schema : defaultSchema;
    // ... rest of implementation
}
```

### In System Prompt

```text
## Available Schemas
- {{defaultSchema}} - Primary transaction data
- {{secondarySchema}} - Reference data
- ANALYTICS - Aggregated reporting data

When user mentions "analytics" or "reports", use the ANALYTICS schema.
```

## Frontend Branding

### Logo and Title

Edit `frontend/src/App.tsx`:

```tsx
<div className="flex items-center gap-3">
  {/* Replace with your logo */}
  <img src="/your-logo.svg" className="w-10 h-10" />
  <div>
    <h1 className="text-xl font-bold">Your App Name</h1>
    <p className="text-sm text-gray-500">Your tagline</p>
  </div>
</div>
```

### Colors

Edit `frontend/tailwind.config.js`:

```js
theme: {
  extend: {
    colors: {
      primary: {
        // Your brand colors
        50: '#your-color',
        // ... etc
        600: '#your-primary',
      },
    },
  },
}
```

### Page Title

Edit `frontend/index.html`:

```html
<title>Your App Name - Your Description</title>
```

## Adding Custom Tools

### 1. Define the Tool

In `DatabaseTools.java`:

```java
@Tool(description = """
    Your tool description here.
    Explain what it does and when to use it.
    """)
public String yourCustomTool(
    @ToolParam(description = "Parameter description") String param1
) {
    // Implementation
    return toJson(result);
}
```

### 2. Update System Prompt

Add the tool to the available tools list:

```text
## Your Capabilities

5. **yourCustomTool** - Description of what it does
   - When to use it
   - Expected parameters
```

### 3. Update Frontend (Optional)

In `ToolCallCard.tsx`:

```tsx
const TOOL_ICONS: Record<string, React.ReactNode> = {
  // ... existing tools
  yourCustomTool: <YourIcon className="w-4 h-4" />,
};

const TOOL_LABELS: Record<string, string> = {
  // ... existing tools
  yourCustomTool: 'Your Tool Label',
};
```

## Environment-Specific Customizations

### Per-Environment Prompts

Create environment-specific prompt files:

```
prompts/
├── system-prompt.txt       # Default
├── system-prompt-dev.txt   # DEV-specific
├── system-prompt-uat.txt   # UAT-specific
└── system-prompt-prod.txt  # PROD-specific
```

In configuration:
```yaml
# application-dev.yml
datalens:
  prompt:
    file: classpath:prompts/system-prompt-dev.txt
```

### Feature Flags

Add feature flags for environment-specific behavior:

```yaml
datalens:
  features:
    allow-prod-queries: ${ALLOW_PROD_QUERIES:false}
    mask-sensitive-data: ${MASK_SENSITIVE:true}
```

Use in code:
```java
@Value("${datalens.features.allow-prod-queries:false}")
private boolean allowProdQueries;
```

## Deployment Checklist

Before deploying to production:

- [ ] Remove mock data source
- [ ] Update package names
- [ ] Customize system prompt
- [ ] Update frontend branding
- [ ] Configure CORS for production domain
- [ ] Set up proper logging
- [ ] Configure monitoring alerts
- [ ] Test with production schemas
- [ ] Review security settings
- [ ] Update documentation

## Troubleshooting

### Common Issues

**"No bean of type ChatModel found"**
- Ensure either `openai` or `azure` profile is active
- Check that API key or certificate is configured

**"Connection refused to Oracle"**
- Verify Kerberos ticket is valid (`klist`)
- Check JDBC URL format
- Verify network connectivity

**"Tool not found by LLM"**
- Ensure `@Tool` annotation has description
- Check that `@ToolParam` annotations are present
- Restart application after changes

### Debug Mode

Enable debug logging:
```bash
export LOG_LEVEL=DEBUG
export SPRING_PROFILES_ACTIVE=dev,mock,openai
```

Check specific components:
```yaml
logging:
  level:
    io.datalens.tools: DEBUG
    org.springframework.ai.chat: DEBUG
    org.springframework.ai.tool: DEBUG
```
