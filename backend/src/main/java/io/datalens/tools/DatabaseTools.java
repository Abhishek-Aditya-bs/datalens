package io.datalens.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datalens.datasource.DataSourceProvider;
import io.datalens.datasource.model.*;
import io.datalens.telemetry.TelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Database tools exposed to the LLM for executing queries and managing connections.
 */
@Component
public class DatabaseTools {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTools.class);

    private final DataSourceProvider dataSource;
    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper;

    @Value("${datalens.schema.default:SCHEMA_A}")
    private String defaultSchema;

    public DatabaseTools(DataSourceProvider dataSource,
                         TelemetryService telemetryService,
                         ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = """
        Execute a SQL SELECT query against the database.
        Only SELECT queries are allowed for safety.
        Always include schema prefix: SCHEMA_NAME.TABLE_NAME
        Use ROWNUM <= N for Oracle or FETCH FIRST N ROWS ONLY to limit results.
        Returns JSON with columnNames, rows, rowCount, and executionTimeMs.
        """)
    public String executeQuery(
            @ToolParam(description = "The SQL SELECT query to execute") String sql) {

        log.info("Executing query: {}", sql);
        telemetryService.recordChatRequest(
                dataSource.getCurrentEnvironment().name(),
                "executeQuery"
        );

        QueryResult result = dataSource.executeQuery(
                sql,
                dataSource.getCurrentEnvironment(),
                defaultSchema
        );

        return toJson(result);
    }

    @Tool(description = """
        Connect to a specific database environment.
        Valid environments: dev, uat, prod (case-insensitive)
        Returns connection status with environment name and success/failure message.
        """)
    public String connectToEnvironment(
            @ToolParam(description = "The environment to connect to: dev, uat, or prod") String env) {

        log.info("Connecting to environment: {}", env);
        telemetryService.recordChatRequest(env.toUpperCase(), "connectToEnvironment");

        try {
            Environment environment = Environment.fromString(env);
            ConnectionStatus status = dataSource.connectToEnvironment(environment);
            return toJson(status);
        } catch (IllegalArgumentException e) {
            return toJson(Map.of(
                    "connected", false,
                    "error", "Invalid environment: " + env + ". Valid options: dev, uat, prod"
            ));
        }
    }

    @Tool(description = """
        Get the current database connection status.
        Returns the currently connected environment and connection health.
        """)
    public String getCurrentStatus() {
        log.info("Getting current status");
        telemetryService.recordChatRequest(dataSource.getCurrentEnvironment().name(), "getCurrentStatus");

        Environment env = dataSource.getCurrentEnvironment();
        ConnectionStatus status = dataSource.testConnection(env);

        Map<String, Object> response = new HashMap<>();
        response.put("currentEnvironment", env.name());
        response.put("connected", status.isConnected());
        response.put("message", status.getMessage());
        response.put("connectionTimeMs", status.getConnectionTimeMs());

        return toJson(response);
    }

    @Tool(description = """
        Get the schema (structure) of a specific table.
        Returns column names, data types, sizes, nullable flags, and primary keys.
        """)
    public String getTableSchema(
            @ToolParam(description = "The name of the table to describe") String tableName,
            @ToolParam(description = "The schema containing the table (optional)") String schema) {

        String targetSchema = (schema == null || schema.isBlank()) ? defaultSchema : schema;
        log.info("Getting schema for table: {}.{}", targetSchema, tableName);
        telemetryService.recordChatRequest(dataSource.getCurrentEnvironment().name(), "getTableSchema");

        TableSchema tableSchema = dataSource.getTableSchema(tableName, targetSchema);

        if (tableSchema == null) {
            return toJson(Map.of("error", "Table not found: " + targetSchema + "." + tableName));
        }

        return toJson(tableSchema);
    }

    @Tool(description = """
        List all available tables in a schema.
        Returns table names, types, and approximate row counts.
        """)
    public String listTables(
            @ToolParam(description = "The schema to list tables from (optional)") String schema) {

        String targetSchema = (schema == null || schema.isBlank()) ? defaultSchema : schema;
        log.info("Listing tables in schema: {}", targetSchema);
        telemetryService.recordChatRequest(dataSource.getCurrentEnvironment().name(), "listTables");

        List<TableInfo> tables = dataSource.listTables(targetSchema);

        Map<String, Object> response = new HashMap<>();
        response.put("schema", targetSchema);
        response.put("tableCount", tables.size());
        response.put("tables", tables);

        return toJson(response);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize to JSON", e);
            return "{\"error\": \"Failed to serialize response\"}";
        }
    }
}
