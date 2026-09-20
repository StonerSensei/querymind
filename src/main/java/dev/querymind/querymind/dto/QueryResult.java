package dev.querymind.querymind.dto;

import java.util.List;
import java.util.Map;

/**
 * The result of the run_query tool.
 *
 * For a SELECT, "rows" holds the returned data and "rowCount" is how many rows
 * came back. For a write (INSERT/UPDATE/DELETE), "rows" is empty and "rowCount"
 * is how many rows were changed.
 *
 * If the result was large and we capped it, "resultsCapped" is true and
 * "rowLimit" is the cap we applied (so the AI can tell the user there is more).
 */
public record QueryResult(
        List<Map<String, Object>> rows,
        int rowCount,
        long executionMs,
        boolean resultsCapped,
        Integer rowLimit) {
}
