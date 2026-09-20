package dev.querymind.querymind.service;

import dev.querymind.querymind.model.QueryAudit;
import dev.querymind.querymind.repository.QueryAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Writes the audit log: one row for every query we handled.
 *
 * Two important rules:
 *   1. It runs on a background thread (@Async), so it never slows down the
 *      user's query.
 *   2. If writing the audit row fails, we only log a warning - an audit problem
 *      must never turn into an error the user sees.
 *
 * Statuses:
 *   SUCCESS      - run_query completed normally
 *   BLOCKED      - run_query rejected by the safety validator
 *   ERROR        - run_query hit a database error
 *   EXPLAIN      - explain_query completed normally
 *   EXPLAIN_ERROR - explain_query hit a database error
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final QueryAuditRepository auditRepository;

    public AuditService(QueryAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    // The query ran fine.
    @Async("auditExecutor")
    public void logSuccess(UUID userId, UUID connectionId, String sql, String prompt,
                           Integer executionMs, Integer rowCount) {
        save(userId, connectionId, sql, prompt, "SUCCESS", null, executionMs, rowCount);
    }

    // The safety validator refused to run the query.
    @Async("auditExecutor")
    public void logBlocked(UUID userId, UUID connectionId, String sql, String prompt, String reason) {
        save(userId, connectionId, sql, prompt, "BLOCKED", reason, null, null);
    }

    // The query ran but the database returned an error (bad SQL, timeout, etc.).
    @Async("auditExecutor")
    public void logError(UUID userId, UUID connectionId, String sql, String prompt, String errorMessage) {
        save(userId, connectionId, sql, prompt, "ERROR", errorMessage, null, null);
    }

    // EXPLAIN ANALYZE ran successfully. Status "EXPLAIN" makes these rows easy
    // to distinguish from normal run_query entries in the audit log.
    @Async("auditExecutor")
    public void logExplain(UUID userId, UUID connectionId, String sql, Integer executionMs) {
        save(userId, connectionId, sql, null, "EXPLAIN", null, executionMs, null);
    }

    // EXPLAIN ANALYZE hit a database error (timeout, unsupported syntax, etc.).
    @Async("auditExecutor")
    public void logExplainError(UUID userId, UUID connectionId, String sql, String errorMessage) {
        save(userId, connectionId, sql, null, "EXPLAIN_ERROR", errorMessage, null, null);
    }

    private void save(UUID userId, UUID connectionId, String sql, String prompt,
                      String status, String errorMessage, Integer executionMs, Integer rowCount) {
        try {
            QueryAudit audit = new QueryAudit();
            audit.setUserId(userId);
            audit.setConnectionId(connectionId);
            audit.setSqlExecuted(sql);
            audit.setPrompt(prompt);
            audit.setStatus(status);
            audit.setErrorMessage(errorMessage);
            audit.setExecutionMs(executionMs);
            audit.setRowCount(rowCount);
            auditRepository.save(audit);
        } catch (Exception e) {
            // Never let an audit problem break or surface to the user's request.
            log.warn("Could not write audit log entry", e);
        }
    }
}