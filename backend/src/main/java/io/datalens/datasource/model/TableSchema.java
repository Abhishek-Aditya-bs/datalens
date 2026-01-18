package io.datalens.datasource.model;

import java.util.List;

/**
 * Schema information for a database table including columns.
 */
public class TableSchema {
    private String schemaName;
    private String tableName;
    private List<ColumnInfo> columns;
    private List<String> primaryKeys;

    public TableSchema() {}

    public TableSchema(String schemaName, String tableName, List<ColumnInfo> columns, List<String> primaryKeys) {
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.columns = columns;
        this.primaryKeys = primaryKeys;
    }

    // Getters
    public String getSchemaName() { return schemaName; }
    public String getTableName() { return tableName; }
    public List<ColumnInfo> getColumns() { return columns; }
    public List<String> getPrimaryKeys() { return primaryKeys; }

    // Setters
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public void setColumns(List<ColumnInfo> columns) { this.columns = columns; }
    public void setPrimaryKeys(List<String> primaryKeys) { this.primaryKeys = primaryKeys; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String schemaName;
        private String tableName;
        private List<ColumnInfo> columns;
        private List<String> primaryKeys;

        public Builder schemaName(String schemaName) { this.schemaName = schemaName; return this; }
        public Builder tableName(String tableName) { this.tableName = tableName; return this; }
        public Builder columns(List<ColumnInfo> columns) { this.columns = columns; return this; }
        public Builder primaryKeys(List<String> primaryKeys) { this.primaryKeys = primaryKeys; return this; }

        public TableSchema build() {
            return new TableSchema(schemaName, tableName, columns, primaryKeys);
        }
    }

    public static class ColumnInfo {
        private String name;
        private String dataType;
        private int size;
        private boolean nullable;
        private String defaultValue;

        public ColumnInfo() {}

        public ColumnInfo(String name, String dataType, int size, boolean nullable, String defaultValue) {
            this.name = name;
            this.dataType = dataType;
            this.size = size;
            this.nullable = nullable;
            this.defaultValue = defaultValue;
        }

        // Getters
        public String getName() { return name; }
        public String getDataType() { return dataType; }
        public int getSize() { return size; }
        public boolean isNullable() { return nullable; }
        public String getDefaultValue() { return defaultValue; }

        // Setters
        public void setName(String name) { this.name = name; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public void setSize(int size) { this.size = size; }
        public void setNullable(boolean nullable) { this.nullable = nullable; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String name;
            private String dataType;
            private int size;
            private boolean nullable;
            private String defaultValue;

            public Builder name(String name) { this.name = name; return this; }
            public Builder dataType(String dataType) { this.dataType = dataType; return this; }
            public Builder size(int size) { this.size = size; return this; }
            public Builder nullable(boolean nullable) { this.nullable = nullable; return this; }
            public Builder defaultValue(String defaultValue) { this.defaultValue = defaultValue; return this; }

            public ColumnInfo build() {
                return new ColumnInfo(name, dataType, size, nullable, defaultValue);
            }
        }
    }
}
