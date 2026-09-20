package dev.querymind.querymind.service;

import dev.querymind.querymind.dto.QueryResult;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * Runs a query that has ALREADY passed the safety validator.
 *
 * It adds two extra safety nets on top of the validator:
 *   1. A query timeout, so one slow query can't run forever.
 *   2. An automatic "LIMIT 500" on any SELECT that didn't ask for a limit, so
 *      we never pull back a huge number of rows.
 *
 * (Read-only mode is enforced separately, on the connection pool itself.)
 */
@Service
public class QueryExecutor {

    // The most rows we return from a SELECT that didn't include its own LIMIT.
    private static final int MAX_ROWS = 500;

    // How long a single query may run before we give up (seconds).
    private static final int QUERY_TIMEOUT_SECONDS = 10;

    public QueryResult execute(DataSource dataSource, String sql) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

        Statement statement = parseQuietly(sql);
        boolean isSelect = statement instanceof Select;

        // For a SELECT with no LIMIT, add "LIMIT 500" so results stay reasonable.
        String finalSql = sql;
        boolean limitInjected = false;
        if (statement instanceof Select select && select.getLimit() == null) {
            Limit limit = new Limit();
            limit.setRowCount(new LongValue(MAX_ROWS));
            select.setLimit(limit);
            finalSql = select.toString();
            limitInjected = true;
        }

        long start = System.currentTimeMillis();

        if (isSelect) {
            List<Map<String, Object>> rows = jdbc.queryForList(finalSql);
            long ms = System.currentTimeMillis() - start;
            boolean capped = limitInjected && rows.size() >= MAX_ROWS;
            return new QueryResult(rows, rows.size(), ms, capped, capped ? MAX_ROWS : null);
        } else {
            int affected = jdbc.update(finalSql);
            long ms = System.currentTimeMillis() - start;
            return new QueryResult(List.of(), affected, ms, false, null);
        }
    }

    /**
     * Runs an EXPLAIN statement and returns the plan as plain text.
     *
     * EXPLAIN returns one text row per line of the plan, so we join them back
     * together with newlines. The same query timeout applies.
     */
    public String explain(DataSource dataSource, String explainSql) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

        // Different databases return the plan differently: Postgres gives one
        // text column, MySQL gives several columns. We read whatever comes back
        // and join each row's values, so this works for both.
        List<Map<String, Object>> rows = jdbc.queryForList(explainSql);
        StringBuilder plan = new StringBuilder();
        for (Map<String, Object> row : rows) {
            List<String> cells = row.values().stream().map(String::valueOf).toList();
            plan.append(String.join(" | ", cells)).append("\n");
        }
        return plan.toString().strip();
    }

    // Parse the SQL. It was already validated, so this normally succeeds.
    private Statement parseQuietly(String sql) {
        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            return null;
        }
    }
}
