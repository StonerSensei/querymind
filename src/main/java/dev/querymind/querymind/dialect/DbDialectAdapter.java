package dev.querymind.querymind.dialect;

/**
 * Every database type (PostgreSQL, MySQL, Oracle) speaks slightly different
 * SQL when it comes to reading the schema. This interface hides those
 * differences.
 *
 * To support a new database later, we just add a new class that implements
 * this interface - the rest of the app does not change.
 */
public interface DbDialectAdapter {

    // Which database type this adapter is for, e.g. "POSTGRES".
    String dbType();

    // Builds the JDBC URL used to connect, e.g. jdbc:postgresql://host:port/db
    String jdbcUrl(String host, int port, String databaseName);

    // SQL that returns one row per table: columns "table_name" and "row_count".
    String listTablesSql();

    // SQL that returns the columns of one table. It has a single "?" which
    // gets filled in with the table name. Returns columns "column_name",
    // "data_type" and "is_nullable".
    String describeTableSql();

    // Wraps a SELECT so the database returns its execution plan instead of the
    // rows. Used by explain_query to find out why a query is slow.
    String explainSql(String selectSql);
}
