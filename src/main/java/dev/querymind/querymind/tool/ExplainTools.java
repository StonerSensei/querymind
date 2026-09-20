package dev.querymind.querymind.tool;

import dev.querymind.querymind.dialect.DbDialectAdapter;
import dev.querymind.querymind.model.DbConnection;
import dev.querymind.querymind.security.AuthContext;
import dev.querymind.querymind.service.AuditService;
import dev.querymind.querymind.service.ConnectionService;
import dev.querymind.querymind.service.ExplanationService;
import dev.querymind.querymind.service.QueryExecutor;
import dev.querymind.querymind.service.SchemaExtractor;
import dev.querymind.querymind.sql.SqlSafetyValidator;
import dev.querymind.querymind.sql.ValidationResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * The explain_query tool: "why is this query slow?".
 *
 * It runs EXPLAIN ANALYZE on the query and returns a plain-English explanation
 * (with index suggestions) if an AI key is configured, otherwise the raw plan.
 *
 * Only SELECT queries are allowed here, because EXPLAIN ANALYZE actually runs
 * the query - we never want that to run a write by accident. We enforce this by
 * validating the SQL as if the connection were read-only, which only lets
 * SELECT through.
 *
 * Schema context (column names and types for each table in the query) is
 * fetched and included in the AI prompt so index suggestions reference real
 * column names rather than being generic.
 *
 * Every call is recorded in the audit log (EXPLAIN on success, EXPLAIN_ERROR
 * on failure) so the full picture of what the AI did is visible in one place.
 */
@Component
public class ExplainTools {

    private final ConnectionService connectionService;
    private final SqlSafetyValidator safetyValidator;
    private final QueryExecutor queryExecutor;
    private final ExplanationService explanationService;
    private final SchemaExtractor schemaExtractor;
    private final AuditService auditService;
    private final AuthContext authContext;

    public ExplainTools(ConnectionService connectionService,
                        SqlSafetyValidator safetyValidator,
                        QueryExecutor queryExecutor,
                        ExplanationService explanationService,
                        SchemaExtractor schemaExtractor,
                        AuditService auditService,
                        AuthContext authContext) {
        this.connectionService = connectionService;
        this.safetyValidator = safetyValidator;
        this.queryExecutor = queryExecutor;
        this.explanationService = explanationService;
        this.schemaExtractor = schemaExtractor;
        this.auditService = auditService;
        this.authContext = authContext;
    }

    @McpTool(name = "explain_query",
            description = "Explains why a SELECT query performs the way it does. Runs EXPLAIN ANALYZE "
                    + "and returns a plain-English explanation with concrete index suggestions based "
                    + "on the actual table schema. Only SELECT queries are allowed.")
    public String explainQuery(
            @McpToolParam(description = "The id of the registered database connection", required = true)
            String connectionId,
            @McpToolParam(description = "The SELECT query to analyze", required = true)
            String sql) {

        UUID userId = authContext.getCurrentUserId();
        UUID connId = UUID.fromString(connectionId);
        DbConnection conn = connectionService.findConnection(connId, userId);

        // Force read-only rules so only SELECT is allowed (EXPLAIN ANALYZE runs
        // the query, so we must never let it run a write).
        ValidationResult check = safetyValidator.validate(sql, true);
        if (!check.allowed()) {
            throw new IllegalArgumentException("Cannot explain this query: " + check.reason());
        }

        DbDialectAdapter adapter = connectionService.getAdapter(conn.getDbType());
        DataSource dataSource = connectionService.getDataSource(conn);

        // Fetch schema context before running EXPLAIN so Claude can reference
        // real column names when suggesting indexes. Failures are non-fatal —
        // SchemaExtractor returns an empty string if it can't describe a table.
        String schemaContext = schemaExtractor.extractSchema(sql, adapter, dataSource);

        long start = System.currentTimeMillis();
        try {
            String plan = queryExecutor.explain(dataSource, adapter.explainSql(sql));
            int executionMs = (int) (System.currentTimeMillis() - start);
            auditService.logExplain(userId, connId, sql, executionMs);
            return explanationService.explain(sql, plan, conn.getDbType(), schemaContext);
        } catch (RuntimeException e) {
            auditService.logExplainError(userId, connId, sql, e.getMessage());
            throw e;
        }
    }
}