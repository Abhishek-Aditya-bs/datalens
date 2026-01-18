package io.datalens.datasource.model;

/**
 * Basic information about a database table.
 */
public class TableInfo {
    private String schemaName;
    private String tableName;
    private String tableType;
    private long rowCount;

    public TableInfo() {}

    public TableInfo(String schemaName, String tableName, String tableType, long rowCount) {
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.tableType = tableType;
        this.rowCount = rowCount;
    }

    // Getters
    public String getSchemaName() { return schemaName; }
    public String getTableName() { return tableName; }
    public String getTableType() { return tableType; }
    public long getRowCount() { return rowCount; }

    // Setters
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public void setTableType(String tableType) { this.tableType = tableType; }
    public void setRowCount(long rowCount) { this.rowCount = rowCount; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String schemaName;
        private String tableName;
        private String tableType;
        private long rowCount;

        public Builder schemaName(String schemaName) { this.schemaName = schemaName; return this; }
        public Builder tableName(String tableName) { this.tableName = tableName; return this; }
        public Builder tableType(String tableType) { this.tableType = tableType; return this; }
        public Builder rowCount(long rowCount) { this.rowCount = rowCount; return this; }

        public TableInfo build() {
            return new TableInfo(schemaName, tableName, tableType, rowCount);
        }
    }
}
