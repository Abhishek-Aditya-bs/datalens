package io.datalens.datasource;

import io.datalens.datasource.model.*;
import io.datalens.telemetry.TelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock data source for local testing without Oracle database.
 * Activated when datalens.datasource.mode=mock
 */
@Component
@ConditionalOnProperty(name = "datalens.datasource.mode", havingValue = "mock", matchIfMissing = true)
public class MockDataSource implements DataSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(MockDataSource.class);

    private final TelemetryService telemetryService;
    private Environment currentEnvironment = Environment.DEV;
    private final Map<String, MockTable> tables = new HashMap<>();

    public MockDataSource(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
        initializeMockData();
    }

    private void initializeMockData() {
        // USERS table
        tables.put("USERS", new MockTable(
                Arrays.asList("ID", "USERNAME", "EMAIL", "STATUS", "CREATED_AT"),
                Arrays.asList(
                        Arrays.asList(1, "john_doe", "john@example.com", "ACTIVE", "2024-01-15"),
                        Arrays.asList(2, "jane_smith", "jane@example.com", "ACTIVE", "2024-02-20"),
                        Arrays.asList(3, "bob_wilson", "bob@example.com", "INACTIVE", "2024-03-10"),
                        Arrays.asList(4, "alice_jones", "alice@example.com", "ACTIVE", "2024-04-05"),
                        Arrays.asList(5, "charlie_brown", "charlie@example.com", "PENDING", "2024-05-12")
                ),
                Arrays.asList(
                        new TableSchema.ColumnInfo("ID", "NUMBER", 10, false, null),
                        new TableSchema.ColumnInfo("USERNAME", "VARCHAR2", 50, false, null),
                        new TableSchema.ColumnInfo("EMAIL", "VARCHAR2", 100, false, null),
                        new TableSchema.ColumnInfo("STATUS", "VARCHAR2", 20, false, "'PENDING'"),
                        new TableSchema.ColumnInfo("CREATED_AT", "DATE", 7, true, "SYSDATE")
                ),
                Collections.singletonList("ID")
        ));

        // ORDERS table
        tables.put("ORDERS", new MockTable(
                Arrays.asList("ORDER_ID", "USER_ID", "PRODUCT_ID", "QUANTITY", "TOTAL_AMOUNT", "ORDER_DATE"),
                Arrays.asList(
                        Arrays.asList(1001, 1, 101, 2, 59.98, "2024-06-01"),
                        Arrays.asList(1002, 2, 102, 1, 149.99, "2024-06-02"),
                        Arrays.asList(1003, 1, 103, 3, 89.97, "2024-06-03"),
                        Arrays.asList(1004, 4, 101, 1, 29.99, "2024-06-04"),
                        Arrays.asList(1005, 3, 104, 2, 199.98, "2024-06-05"),
                        Arrays.asList(1006, 2, 105, 1, 499.99, "2024-06-06"),
                        Arrays.asList(1007, 5, 102, 2, 299.98, "2024-06-07")
                ),
                Arrays.asList(
                        new TableSchema.ColumnInfo("ORDER_ID", "NUMBER", 10, false, null),
                        new TableSchema.ColumnInfo("USER_ID", "NUMBER", 10, false, null),
                        new TableSchema.ColumnInfo("PRODUCT_ID", "NUMBER", 10, false, null),
                        new TableSchema.ColumnInfo("QUANTITY", "NUMBER", 5, false, "1"),
                        new TableSchema.ColumnInfo("TOTAL_AMOUNT", "NUMBER", 10, false, null),
                        new TableSchema.ColumnInfo("ORDER_DATE", "DATE", 7, false, "SYSDATE")
                ),
                Collections.singletonList("ORDER_ID")
        ));

        // PRODUCTS table
        tables.put("PRODUCTS", new MockTable(
                Arrays.asList("PRODUCT_ID", "NAME", "CATEGORY", "PRICE", "STOCK_QUANTITY"),
                Arrays.asList(
                        Arrays.asList(101, "Wireless Mouse", "Electronics", 29.99, 150),
                        Arrays.asList(102, "Mechanical Keyboard", "Electronics", 149.99, 75),
                        Arrays.asList(103, "USB-C Hub", "Electronics", 29.99, 200),
                        Arrays.asList(104, "Monitor Stand", "Accessories", 99.99, 50),
                        Arrays.asList(105, "Webcam 4K", "Electronics", 499.99, 30)
                ),
                Arrays.asList(
                        new TableSchema.ColumnInfo("PRODUCT_ID", "NUMBER", 10, false, null),
                        new TableSchema.ColumnInfo("NAME", "VARCHAR2", 100, false, null),
                        new TableSchema.ColumnInfo("CATEGORY", "VARCHAR2", 50, false, null),
                        new TableSchema.ColumnInfo("PRICE", "NUMBER", 10, false, null),
                        new TableSchema.ColumnInfo("STOCK_QUANTITY", "NUMBER", 10, false, "0")
                ),
                Collections.singletonList("PRODUCT_ID")
        ));

        log.info("Mock data initialized with {} tables", tables.size());
    }

    @Override
    public QueryResult executeQuery(String sql, Environment env, String schema) {
        long startTime = System.currentTimeMillis();
        simulateDelay(50, 200);

        String trimmedSql = sql.trim().toUpperCase();
        if (!trimmedSql.startsWith("SELECT")) {
            return QueryResult.error("Only SELECT queries are allowed", System.currentTimeMillis() - startTime);
        }

        try {
            QueryResult result = executeMockQuery(sql);
            long executionTime = System.currentTimeMillis() - startTime;
            telemetryService.recordQueryDuration(env.name(), executionTime);

            return QueryResult.builder()
                    .columnNames(result.getColumnNames())
                    .rows(result.getRows())
                    .rowCount(result.getRows().size())
                    .executionTimeMs(executionTime)
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("Mock query execution failed: {}", e.getMessage());
            return QueryResult.error(e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    private QueryResult executeMockQuery(String sql) {
        Pattern fromPattern = Pattern.compile("FROM\\s+(?:\\w+\\.)?([\\w]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = fromPattern.matcher(sql);

        if (!matcher.find()) {
            throw new IllegalArgumentException("Could not parse table name from query");
        }

        String tableName = matcher.group(1).toUpperCase();
        MockTable table = tables.get(tableName);

        if (table == null) {
            throw new IllegalArgumentException("Table not found: " + tableName);
        }

        List<List<Object>> resultRows = new ArrayList<>(table.rows);
        List<String> resultColumns = new ArrayList<>(table.columns);
        String upperSql = sql.toUpperCase();

        // Handle SELECT specific columns
        if (!upperSql.contains("SELECT *")) {
            Pattern selectPattern = Pattern.compile("SELECT\\s+(.+?)\\s+FROM", Pattern.CASE_INSENSITIVE);
            Matcher selectMatcher = selectPattern.matcher(sql);
            if (selectMatcher.find()) {
                String columnsPart = selectMatcher.group(1);
                List<String> selectedColumns = Arrays.stream(columnsPart.split(","))
                        .map(c -> c.trim().toUpperCase())
                        .toList();

                List<Integer> columnIndices = new ArrayList<>();
                for (String col : selectedColumns) {
                    int idx = table.columns.indexOf(col);
                    if (idx >= 0) {
                        columnIndices.add(idx);
                    }
                }

                if (!columnIndices.isEmpty()) {
                    resultColumns = selectedColumns;
                    resultRows = resultRows.stream()
                            .map(row -> columnIndices.stream().map(row::get).toList())
                            .toList();
                }
            }
        }

        // Handle LIMIT/ROWNUM
        Pattern limitPattern = Pattern.compile("(?:LIMIT|ROWNUM\\s*<=?)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher limitMatcher = limitPattern.matcher(sql);
        if (limitMatcher.find()) {
            int limit = Integer.parseInt(limitMatcher.group(1));
            if (resultRows.size() > limit) {
                resultRows = resultRows.subList(0, limit);
            }
        }

        return QueryResult.builder()
                .columnNames(resultColumns)
                .rows(new ArrayList<>(resultRows))
                .build();
    }

    @Override
    public ConnectionStatus testConnection(Environment env) {
        simulateDelay(20, 100);
        return ConnectionStatus.builder()
                .environment(env)
                .connected(true)
                .message("[MOCK] Connection successful to " + env)
                .connectionTimeMs(50)
                .build();
    }

    @Override
    public ConnectionStatus connectToEnvironment(Environment env) {
        simulateDelay(50, 150);
        this.currentEnvironment = env;
        log.info("[MOCK] Connected to {} environment", env);
        return ConnectionStatus.builder()
                .environment(env)
                .connected(true)
                .message("[MOCK] Successfully connected to " + env)
                .connectionTimeMs(100)
                .build();
    }

    @Override
    public Environment getCurrentEnvironment() {
        return currentEnvironment;
    }

    @Override
    public List<TableInfo> listTables(String schema) {
        simulateDelay(30, 100);
        return tables.entrySet().stream()
                .map(entry -> TableInfo.builder()
                        .schemaName(schema)
                        .tableName(entry.getKey())
                        .tableType("TABLE")
                        .rowCount(entry.getValue().rows.size())
                        .build())
                .toList();
    }

    @Override
    public TableSchema getTableSchema(String tableName, String schema) {
        simulateDelay(20, 80);
        MockTable table = tables.get(tableName.toUpperCase());
        if (table == null) {
            return null;
        }
        return TableSchema.builder()
                .schemaName(schema)
                .tableName(tableName)
                .columns(table.columnInfos)
                .primaryKeys(table.primaryKeys)
                .build();
    }

    private void simulateDelay(int minMs, int maxMs) {
        try {
            Thread.sleep(minMs + (int) (Math.random() * (maxMs - minMs)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record MockTable(
            List<String> columns,
            List<List<Object>> rows,
            List<TableSchema.ColumnInfo> columnInfos,
            List<String> primaryKeys
    ) {}
}
