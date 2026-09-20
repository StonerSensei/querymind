package dev.querymind.querymind.controller;

import dev.querymind.querymind.model.QueryAudit;
import dev.querymind.querymind.repository.QueryAuditRepository;
import dev.querymind.querymind.security.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lets a logged-in user review their own audit log - every query they ran, its
 * status (SUCCESS/BLOCKED/ERROR), how long it took and how many rows it touched.
 *
 * A user only ever sees their own history (tenant isolation).
 */
@RestController
@RequestMapping("/audit")
public class AuditController {

    private final QueryAuditRepository auditRepository;
    private final AuthContext authContext;

    public AuditController(QueryAuditRepository auditRepository, AuthContext authContext) {
        this.auditRepository = auditRepository;
        this.authContext = authContext;
    }

    @GetMapping
    public List<QueryAudit> myAuditLog() {
        UUID userId = authContext.getCurrentUserId();
        return auditRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
