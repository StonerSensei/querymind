package dev.querymind.querymind.repository;

import dev.querymind.querymind.model.DbConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Database access for registered database connections.
 */
public interface DbConnectionRepository extends JpaRepository<DbConnection, UUID> {

    // All connections that belong to one user.
    List<DbConnection> findByUserId(UUID userId);

    // Find a connection but only if it belongs to this user.
    // This is how we stop one user from touching another user's connection.
    Optional<DbConnection> findByIdAndUserId(UUID id, UUID userId);
}
