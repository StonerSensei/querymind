package dev.querymind.querymind.repository;

import dev.querymind.querymind.model.QueryAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Database access for the audit log.
 */
public interface QueryAuditRepository extends JpaRepository<QueryAudit, UUID> {

    // Show a user's audit history, newest first.
    List<QueryAudit> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
