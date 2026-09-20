package dev.querymind.querymind.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One row for every query the AI agent tried to run.
 *
 * We store the SQL, whether it succeeded, how long it took and how many
 * rows came back. This gives admins a full history of what the AI did.
 */
@Entity
@Table(name = "query_audit")
@Getter
@Setter
public class QueryAudit {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    // The actual SQL we ran (or tried to run).
    @Column(name = "sql_executed", nullable = false, columnDefinition = "text")
    private String sqlExecuted;

    // The user's plain-English question that led to this SQL, if the client
    // sent it. Optional, so it may be null.
    @Column(columnDefinition = "text")
    private String prompt;

    // SUCCESS, ERROR or BLOCKED.
    @Column(nullable = false)
    private String status;

    // Filled in only when something went wrong.
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    // How long the query took, in milliseconds.
    @Column(name = "execution_ms")
    private Integer executionMs;

    // How many rows came back (null if the query failed).
    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
