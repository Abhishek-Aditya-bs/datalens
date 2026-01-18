package io.datalens.datasource.model;

/**
 * Represents a database schema.
 */
public class Schema {
    private String name;
    private String description;

    public Schema() {}

    public Schema(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
