package dev.querymind.querymind.service;

import dev.querymind.querymind.dialect.DbDialectAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts the schema (column names, types, nullability) for every table
 * referenced in a SQL query.
 *
 * This is used by explain_query to give Claude the table structure alongside
 * the query plan. Knowing that "customer_id" is an unindexed integer column
 * lets Claude give a specific "add an index on customer_id" suggestion instead
 * of a generic one.
 *
 * Table extraction uses the same JSqlParser AST that the safety validator uses,
 * so it works on any SQL that has already passed validation. Failures here are
 * non-fatal: if we can't describe a table (it might be a subquery alias, a CTE,
 * or a temp table), we just skip it.
 */
@Service
public class SchemaExtractor {

    private static final Logger log = LoggerFactory.getLogger(SchemaExtractor.class);

    /**
     * Returns a human-readable schema block for the prompt, e.g.:
     *
     *   Table: customers
     *     id          integer       NOT NULL
     *     name        varchar       NOT NULL
     *     country     varchar       nullable
     *
     *   Table: orders
     *     id          integer       NOT NULL
     *     customer_id integer       NOT NULL
     *     total       numeric       NOT NULL
     *
     * Returns an empty string if no tables can be extracted or described,
     * so callers don't need to handle null.
     */
    public String extractSchema(String sql, DbDialectAdapter adapter, DataSource dataSource) {
        List<String> tableNames = parseTableNames(sql);
        if (tableNames.isEmpty()) {
            return "";
        }

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        StringBuilder sb = new StringBuilder();

        for (String table : tableNames) {
            String schema = describeTable(table, adapter, jdbc);
            if (!schema.isEmpty()) {
                sb.append(schema).append("\n");
            }
        }

        return sb.toString().strip();
    }

    // Pull table names out of the SQL AST. Subquery aliases and CTEs will be
    // included in the raw list — the describe step silently drops them when the
    // DB doesn't recognise the name.
    private List<String> parseTableNames(String sql) {
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            TablesNamesFinder finder = new TablesNamesFinder();
            return finder.getTableList(stmt);
        } catch (Exception e) {
            log.debug("Could not parse table names from SQL: {}", e.getMessage());
            return List.of();
        }
    }

    // Run the adapter's describe query and format the result as a readable block.
    private String describeTable(String tableName, DbDialectAdapter adapter, JdbcTemplate jdbc) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(adapter.describeTableSql(), tableName);
            if (rows.isEmpty()) {
                return ""; // not a real table (CTE alias, subquery, etc.)
            }

            StringBuilder sb = new StringBuilder("Table: ").append(tableName).append("\n");
            for (Map<String, Object> row : rows) {
                String col      = String.valueOf(row.get("column_name"));
                String type     = String.valueOf(row.get("data_type"));
                String nullable = "YES".equalsIgnoreCase(String.valueOf(row.get("is_nullable")))
                        ? "nullable" : "NOT NULL";
                sb.append(String.format("  %-20s %-16s %s%n", col, type, nullable));
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("Could not describe table '{}': {}", tableName, e.getMessage());
            return "";
        }
    }
}