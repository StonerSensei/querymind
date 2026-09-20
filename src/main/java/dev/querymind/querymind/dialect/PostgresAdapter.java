package dev.querymind.querymind.dialect;

import org.springframework.stereotype.Component;

/**
 * The PostgreSQL version of the schema queries.
 */
@Component
public class PostgresAdapter implements DbDialectAdapter {

    @Override
    public String dbType() {
        return "POSTGRES";
    }

    @Override
    public String jdbcUrl(String host, int port, String databaseName) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + databaseName;
    }

    @Override
    public String listTablesSql() {
        // "reltuples" is the row count Postgres estimates for each table.
        // It is fast to read (no table scan) and is refreshed by ANALYZE.
        // relkind = 'r' means an ordinary table.
        return "SELECT c.relname AS table_name, c.reltuples::bigint AS row_count "
                + "FROM pg_class c "
                + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = 'public' AND c.relkind = 'r' "
                + "ORDER BY c.relname";
    }

    @Override
    public String describeTableSql() {
        // The "?" is replaced with the table name when the query runs.
        return "SELECT column_name, data_type, is_nullable "
                + "FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = ? "
                + "ORDER BY ordinal_position";
    }

    @Override
    public String explainSql(String selectSql) {
        // ANALYZE actually runs the query and reports real timings and row
        // counts - that is what makes the plan useful for spotting slow parts.
        return "EXPLAIN ANALYZE " + selectSql;
    }
}
