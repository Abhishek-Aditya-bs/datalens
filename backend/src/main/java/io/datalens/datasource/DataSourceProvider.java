package io.datalens.datasource;

import io.datalens.datasource.model.*;

import java.util.List;

/**
 * Interface for database operations abstraction.
 * Implementations include OracleDataSource (production) and MockDataSource (testing).
 */
public interface DataSourceProvider {

    /**
     * Executes a SELECT SQL query.
     *
     * @param sql    The SQL query to execute (must be SELECT only)
     * @param env    The target environment
     * @param schema The schema to query against
     * @return QueryResult containing the results or error
     */
    QueryResult executeQuery(String sql, Environment env, String schema);

    /**
     * Tests connection to the specified environment.
     *
     * @param env The environment to test
     * @return ConnectionStatus with connection details
     */
    ConnectionStatus testConnection(Environment env);

    /**
     * Connects to the specified environment.
     *
     * @param env The environment to connect to
     * @return ConnectionStatus with connection details
     */
    ConnectionStatus connectToEnvironment(Environment env);

    /**
     * Gets the current environment.
     *
     * @return The currently connected environment
     */
    Environment getCurrentEnvironment();

    /**
     * Lists all tables in the specified schema.
     *
     * @param schema The schema to list tables from
     * @return List of TableInfo objects
     */
    List<TableInfo> listTables(String schema);

    /**
     * Gets the schema information for a specific table.
     *
     * @param tableName The table name
     * @param schema    The schema containing the table
     * @return TableSchema with column information
     */
    TableSchema getTableSchema(String tableName, String schema);
}
