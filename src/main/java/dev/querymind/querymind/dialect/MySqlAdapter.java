package dev.querymind.querymind.dialect;

import org.springframework.stereotype.Component;

/**
 * The MySQL version of the schema queries.
 *
 * MySQL keeps the same information in information_schema as PostgreSQL, but the
 * "current database" is found with DATABASE() and the approximate row count is
 * the TABLE_ROWS column. We alias every column to the same names the rest of
 * the app expects (table_name, row_count, ...), so nothing else has to change.
 */
@Component
public class MySqlAdapter implements DbDialectAdapter {

    @Override
    public String dbType() {
        return "MYSQL";
    }

    @Override
    public String jdbcUrl(String host, int port, String databaseName) {
        return "jdbc:mysql://" + host + ":" + port + "/" + databaseName;
    }

    @Override
    public String listTablesSql() {
        return "SELECT table_name AS table_name, table_rows AS row_count "
                + "FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                + "ORDER BY table_name";
    }

    @Override
    public String describeTableSql() {
        // The "?" is replaced with the table name when the query runs.
        return "SELECT column_name AS column_name, data_type AS data_type, "
                + "is_nullable AS is_nullable "
                + "FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? "
                + "ORDER BY ordinal_position";
    }

    @Override
    public String explainSql(String selectSql) {
        // Plain EXPLAIN works on every MySQL version and does NOT run the query.
        return "EXPLAIN " + selectSql;
    }
}
