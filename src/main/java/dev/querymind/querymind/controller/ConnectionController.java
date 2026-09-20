package dev.querymind.querymind.controller;

import dev.querymind.querymind.dto.ConnectionResponse;
import dev.querymind.querymind.dto.CreateConnectionRequest;
import dev.querymind.querymind.dto.TestConnectionResponse;
import dev.querymind.querymind.security.AuthContext;
import dev.querymind.querymind.service.ConnectionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for a user to manage their own database connections.
 *
 * All endpoints need a valid JWT and only ever act on the logged-in user's own
 * connections. Passwords are never returned.
 */
@RestController
@RequestMapping("/connections")
public class ConnectionController {

    private final ConnectionService connectionService;
    private final AuthContext authContext;

    public ConnectionController(ConnectionService connectionService, AuthContext authContext) {
        this.connectionService = connectionService;
        this.authContext = authContext;
    }

    // Register a new connection.
    @PostMapping
    public ConnectionResponse register(@Valid @RequestBody CreateConnectionRequest request) {
        UUID userId = authContext.getCurrentUserId();
        return ConnectionResponse.from(connectionService.registerConnection(userId, request));
    }

    // List my connections.
    @GetMapping
    public List<ConnectionResponse> list() {
        UUID userId = authContext.getCurrentUserId();
        return connectionService.listConnections(userId).stream()
                .map(ConnectionResponse::from)
                .toList();
    }

    // Delete one of my connections.
    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        UUID userId = authContext.getCurrentUserId();
        connectionService.deleteConnection(id, userId);
    }

    // Check that one of my connections actually works.
    @PostMapping("/{id}/test")
    public TestConnectionResponse test(@PathVariable UUID id) {
        UUID userId = authContext.getCurrentUserId();
        return connectionService.testConnection(id, userId);
    }
}
