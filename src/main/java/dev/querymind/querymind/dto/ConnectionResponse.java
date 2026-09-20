package dev.querymind.querymind.dto;

import dev.querymind.querymind.model.DbConnection;

import java.time.Instant;
import java.util.UUID;

/**
 * What we send back about a connection. Notice there is NO password here - we
 * never expose the stored password (not even the encrypted form).
 */
public record ConnectionResponse(
        UUID id,
        String name,
        String dbType,
        String host,
        int port,
        String databaseName,
        String username,
        boolean readOnly,
        Instant createdAt) {

    public static ConnectionResponse from(DbConnection c) {
        return new ConnectionResponse(
                c.getId(), c.getName(), c.getDbType(), c.getHost(), c.getPort(),
                c.getDatabaseName(), c.getUsername(), c.isReadOnly(), c.getCreatedAt());
    }
}
