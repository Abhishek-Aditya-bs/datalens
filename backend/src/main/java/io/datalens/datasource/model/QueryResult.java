package io.datalens.datasource.model;

import java.util.List;

/**
 * Represents the result of a database query.
 */
public class QueryResult {
    private List<String> columnNames;
    private List<List<Object>> rows;
    private int rowCount;
    private long executionTimeMs;
    private String error;
    private boolean success;

    public QueryResult() {}

    private QueryResult(List<String> columnNames, List<List<Object>> rows, int rowCount,
                        long executionTimeMs, String error, boolean success) {
        this.columnNames = columnNames;
        this.rows = rows;
        this.rowCount = rowCount;
        this.executionTimeMs = executionTimeMs;
        this.error = error;
        this.success = success;
    }

    public static QueryResult success(List<String> columnNames, List<List<Object>> rows, long executionTimeMs) {
        return new QueryResult(columnNames, rows, rows.size(), executionTimeMs, null, true);
    }

    public static QueryResult error(String errorMessage, long executionTimeMs) {
        return new QueryResult(null, null, 0, executionTimeMs, errorMessage, false);
    }

    // Getters
    public List<String> getColumnNames() { return columnNames; }
    public List<List<Object>> getRows() { return rows; }
    public int getRowCount() { return rowCount; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public String getError() { return error; }
    public boolean isSuccess() { return success; }

    // Setters
    public void setColumnNames(List<String> columnNames) { this.columnNames = columnNames; }
    public void setRows(List<List<Object>> rows) { this.rows = rows; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    public void setError(String error) { this.error = error; }
    public void setSuccess(boolean success) { this.success = success; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private List<String> columnNames;
        private List<List<Object>> rows;
        private int rowCount;
        private long executionTimeMs;
        private String error;
        private boolean success;

        public Builder columnNames(List<String> columnNames) { this.columnNames = columnNames; return this; }
        public Builder rows(List<List<Object>> rows) { this.rows = rows; return this; }
        public Builder rowCount(int rowCount) { this.rowCount = rowCount; return this; }
        public Builder executionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder success(boolean success) { this.success = success; return this; }

        public QueryResult build() {
            return new QueryResult(columnNames, rows, rowCount, executionTimeMs, error, success);
        }
    }
}
