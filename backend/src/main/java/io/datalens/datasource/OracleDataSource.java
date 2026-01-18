package io.datalens.datasource;

import io.datalens.config.DatabaseConfig;
import io.datalens.datasource.model.*;
import io.datalens.telemetry.TelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oracle database implementation using Kerberos authentication.
 * Activated when datalens.datasource.mode=oracle
 */
@Component
@ConditionalOnProperty(name = "datalens.datasource.mode", havingValue = "oracle")
public class OracleDataSource implements DataSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(OracleDataSource.class);

    private final DatabaseConfig config;
    private final TelemetryService telemetryService;
    private final Map<Environment, Connection> connectionPool = new ConcurrentHashMap<>();
    private Environment currentEnvironment = Environment.DEV;

    public OracleDataSource(DatabaseConfig config, TelemetryService telemetryService) {
        this.config = config;
        this.telemetryService = telemetryService;
    }

    @Override
    public QueryResult executeQuery(String sql, Environment env, String schema) {
        long startTime = System.currentTimeMillis();

        String trimmedSql = sql.trim().toUpperCase();
        if (!trimmedSql.startsWith("SELECT")) {
            return QueryResult.error("Only SELECT queries are allowed", 0);
        }

        try {
            Connection conn = getConnection(env);
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(config.getQueryTimeoutSeconds());

                ResultSet rs = stmt.executeQuery(sql);
                ResultSetMetaData metaData = rs.getMetaData();

                List<String> columnNames = new ArrayList<>();
                int columnCount = metaData.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    columnNames.add(metaData.getColumnName(i));
                }

                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(rs.getObject(i));
                    }
                    rows.add(row);
                }

                long executionTime = System.currentTimeMillis() - startTime;
                telemetryService.recordQueryDuration(env.name(), executionTime);

                return QueryResult.success(columnNames, rows, executionTime);
            }
        } catch (SQLException e) {
            log.error("Query execution failed: {}", e.getMessage(), e);
            long executionTime = System.currentTimeMillis() - startTime;
            return QueryResult.error(e.getMessage(), executionTime);
        }
    }

    @Override
    public ConnectionStatus testConnection(Environment env) {
        long startTime = System.currentTimeMillis();
        try {
            Connection conn = getConnection(env);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1 FROM DUAL");
            }
            long connectionTime = System.currentTimeMillis() - startTime;
            return ConnectionStatus.builder()
                    .environment(env)
                    .connected(true)
                    .message("Connection successful")
                    .connectionTimeMs(connectionTime)
                    .build();
        } catch (SQLException e) {
            long connectionTime = System.currentTimeMillis() - startTime;
            return ConnectionStatus.builder()
                    .environment(env)
                    .connected(false)
                    .message("Connection failed: " + e.getMessage())
                    .connectionTimeMs(connectionTime)
                    .build();
        }
    }

    @Override
    public ConnectionStatus connectToEnvironment(Environment env) {
        long startTime = System.currentTimeMillis();
        try {
            if (currentEnvironment != env && connectionPool.containsKey(currentEnvironment)) {
                Connection oldConn = connectionPool.remove(currentEnvironment);
                if (oldConn != null && !oldConn.isClosed()) {
                    oldConn.close();
                }
            }

            Connection conn = getConnection(env);
            this.currentEnvironment = env;

            long connectionTime = System.currentTimeMillis() - startTime;
            log.info("Connected to {} environment in {}ms", env, connectionTime);

            return ConnectionStatus.builder()
                    .environment(env)
                    .connected(true)
                    .message("Successfully connected to " + env)
                    .connectionTimeMs(connectionTime)
                    .build();
        } catch (SQLException e) {
            long connectionTime = System.currentTimeMillis() - startTime;
            log.error("Failed to connect to {}: {}", env, e.getMessage(), e);
            return ConnectionStatus.builder()
                    .environment(env)
                    .connected(false)
                    .message("Connection failed: " + e.getMessage())
                    .connectionTimeMs(connectionTime)
                    .build();
        }
    }

    @Override
    public Environment getCurrentEnvironment() {
        return currentEnvironment;
    }

    @Override
    public List<TableInfo> listTables(String schema) {
        String sql = """
            SELECT TABLE_NAME, TABLE_TYPE, NUM_ROWS
            FROM ALL_TABLES
            WHERE OWNER = ?
            ORDER BY TABLE_NAME
            """;

        List<TableInfo> tables = new ArrayList<>();
        try {
            Connection conn = getConnection(currentEnvironment);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, schema.toUpperCase());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    tables.add(TableInfo.builder()
                            .schemaName(schema)
                            .tableName(rs.getString("TABLE_NAME"))
                            .tableType(rs.getString("TABLE_TYPE"))
                            .rowCount(rs.getLong("NUM_ROWS"))
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("Failed to list tables: {}", e.getMessage(), e);
        }
        return tables;
    }

    @Override
    public TableSchema getTableSchema(String tableName, String schema) {
        String columnSql = """
            SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE, DATA_DEFAULT
            FROM ALL_TAB_COLUMNS
            WHERE OWNER = ? AND TABLE_NAME = ?
            ORDER BY COLUMN_ID
            """;

        String pkSql = """
            SELECT COLUMN_NAME
            FROM ALL_CONS_COLUMNS acc
            JOIN ALL_CONSTRAINTS ac ON acc.CONSTRAINT_NAME = ac.CONSTRAINT_NAME
            WHERE ac.OWNER = ? AND ac.TABLE_NAME = ? AND ac.CONSTRAINT_TYPE = 'P'
            ORDER BY acc.POSITION
            """;

        try {
            Connection conn = getConnection(currentEnvironment);
            List<TableSchema.ColumnInfo> columns = new ArrayList<>();
            List<String> primaryKeys = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(columnSql)) {
                stmt.setString(1, schema.toUpperCase());
                stmt.setString(2, tableName.toUpperCase());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    columns.add(TableSchema.ColumnInfo.builder()
                            .name(rs.getString("COLUMN_NAME"))
                            .dataType(rs.getString("DATA_TYPE"))
                            .size(rs.getInt("DATA_LENGTH"))
                            .nullable("Y".equals(rs.getString("NULLABLE")))
                            .defaultValue(rs.getString("DATA_DEFAULT"))
                            .build());
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(pkSql)) {
                stmt.setString(1, schema.toUpperCase());
                stmt.setString(2, tableName.toUpperCase());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
            }

            return TableSchema.builder()
                    .schemaName(schema)
                    .tableName(tableName)
                    .columns(columns)
                    .primaryKeys(primaryKeys)
                    .build();
        } catch (SQLException e) {
            log.error("Failed to get table schema: {}", e.getMessage(), e);
            return null;
        }
    }

    private Connection getConnection(Environment env) throws SQLException {
        Connection conn = connectionPool.get(env);
        if (conn != null && !conn.isClosed()) {
            return conn;
        }

        String url = config.getDatabaseUrl(env);
        Properties props = config.getConnectionProperties(env);

        conn = DriverManager.getConnection(url, props);
        connectionPool.put(env, conn);
        return conn;
    }
}
