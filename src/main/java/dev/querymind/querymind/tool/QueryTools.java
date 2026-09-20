package dev.querymind.querymind.tool;

import dev.querymind.querymind.dto.QueryResult;
import dev.querymind.querymind.model.DbConnection;
import dev.querymind.querymind.security.AuthContext;
import dev.querymind.querymind.service.AuditService;
import dev.querymind.querymind.service.ConnectionService;
import dev.querymind.querymind.service.QueryExecutor;
import dev.querymind.querymind.sql.SqlSafetyValidator;
import dev.querymind.querymind.sql.ValidationResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * The run_query tool. This is where an AI-written query actually runs against a
 * user's database - but only after it passes the safety validator.
 *
 * The order is important: check who is calling, check they own the connection,
 * check the SQL is safe, and only then run it.
 */
@Component
public class QueryTools {

    private final ConnectionService connectionService;
    private final SqlSafetyValidator safetyValidator;
    private final QueryExecutor queryExecutor;
    private final AuthContext authContext;
    private final AuditService auditService;

    public QueryTools(ConnectionService connectionService,
                      SqlSafetyValidator safetyValidator,
                      QueryExecutor queryExecutor,
                      AuthContext authContext,
                      AuditService auditService) {
        this.connectionService = connectionService;
        this.safetyValidator = safetyValidator;
        this.queryExecutor = queryExecutor;
        this.authContext = authContext;
        this.auditService = auditService;
    }

    @McpTool(name = "run_query",
            description = "Runs a single SQL query against a registered database and returns the rows. "
                    + "Dangerous statements (DROP, TRUNCATE, DELETE/UPDATE without WHERE) are blocked, "
                    + "read-only connections reject writes, and large results are capped at 500 rows.")
    public QueryResult runQuery(
            @McpToolParam(description = "The id of the registered database connection", required = true)
            String connectionId,
            @McpToolParam(description = "The SQL query to run", required = true)
            String sql,
            @McpToolParam(description = "Optional: the user's original question in plain English, "
                    + "stored in the audit log so admins can see what the query was for", required = false)
            String prompt) {

        UUID userId = authContext.getCurrentUserId();
        UUID connId = UUID.fromString(connectionId);
        DbConnection conn = connectionService.findConnection(connId, userId);

        // Safety first: reject anything dangerous before we touch the database.
        ValidationResult check = safetyValidator.validate(sql, conn.isReadOnly());
        if (!check.allowed()) {
            auditService.logBlocked(userId, connId, sql, prompt, check.reason());
            throw new IllegalArgumentException("Query blocked: " + check.reason());
        }

        DataSource dataSource = connectionService.getDataSource(conn);
        try {
            QueryResult result = queryExecutor.execute(dataSource, sql);
            auditService.logSuccess(userId, connId, sql, prompt,
                    (int) result.executionMs(), result.rowCount());
            return result;
        } catch (RuntimeException e) {
            // The database rejected the query (bad SQL, timeout, etc.).
            auditService.logError(userId, connId, sql, prompt, e.getMessage());
            throw e;
        }
    }
}
