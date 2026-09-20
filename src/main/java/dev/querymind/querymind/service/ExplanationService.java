package dev.querymind.querymind.service;

import org.springframework.stereotype.Service;

/**
 * Turns a raw database query plan into a plain-English explanation.
 *
 * If the Anthropic API is set up, we ask Claude to explain the plan and suggest
 * improvements. If it isn't (or the call fails), we just hand back the raw plan
 * with a short note - which is still useful on its own.
 *
 * The prompt is tailored to the database engine so Claude uses the right
 * terminology and interprets the plan format correctly. It also includes the
 * schema (column names and types) for every table in the query, so Claude can
 * make concrete index suggestions rather than generic ones.
 */
@Service
public class ExplanationService {

    private final AnthropicClient anthropicClient;

    public ExplanationService(AnthropicClient anthropicClient) {
        this.anthropicClient = anthropicClient;
    }

    /**
     * @param sql           the original SELECT query
     * @param queryPlan     the raw output from EXPLAIN (ANALYZE) as a single string
     * @param dbType        the database engine, e.g. "POSTGRES" or "MYSQL"
     * @param schemaContext column definitions for tables in the query, or empty string
     */
    public String explain(String sql, String queryPlan, String dbType, String schemaContext) {
        if (!anthropicClient.isConfigured()) {
            return "AI explanation is turned off (no Anthropic API key set). "
                    + "Here is the raw query plan:\n\n" + queryPlan;
        }

        String prompt = buildPrompt(sql, queryPlan, dbType, schemaContext);

        String answer = anthropicClient.complete(prompt);
        if (answer == null) {
            return "Could not get an AI explanation right now. Here is the raw query plan:\n\n"
                    + queryPlan;
        }
        return answer;
    }

    private String buildPrompt(String sql, String queryPlan, String dbType, String schemaContext) {
        boolean isMysql = "MYSQL".equalsIgnoreCase(dbType);

        String expertRole = isMysql
                ? "You are a MySQL performance expert."
                : "You are a PostgreSQL performance expert.";

        String planLabel = isMysql
                ? "Here is the EXPLAIN output (MySQL tabular format):"
                : "Here is the EXPLAIN ANALYZE output (PostgreSQL text format, with actual timings):";

        String planNotes = isMysql
                ? """
                  Key columns to focus on: type (ALL = full scan, ref/eq_ref = index use),
                  key (which index was chosen, or NULL if none), rows (estimated rows scanned),
                  and Extra (Using filesort, Using temporary are red flags)."""
                : """
                  Key terms: Seq Scan = no index used, Index Scan = index used, \
                  actual rows vs estimated rows (large divergence = stale statistics), \
                  actual time is in milliseconds.""";

        // Only include the schema block when we actually have column data.
        String schemaSection = schemaContext == null || schemaContext.isBlank()
                ? ""
                : """

                  Here is the schema for the tables involved:

                  %s
                  """.formatted(schemaContext);

        return """
                %s A user ran this query:

                %s
                %s
                %s

                %s

                %s

                In plain English, explain why the query performs the way it does,
                point out the slow parts, and suggest concrete improvements such
                as indexes (use the actual column names from the schema above).
                Keep the answer short and practical.
                """.formatted(expertRole, sql, schemaSection, planLabel, queryPlan, planNotes);
    }
}