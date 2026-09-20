package dev.querymind.querymind.repository;

import dev.querymind.querymind.model.SchemaCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Database access for cached schemas.
 */
public interface SchemaCacheRepository extends JpaRepository<SchemaCache, UUID> {

    // Get the cached schema for one connection, if we have one.
    Optional<SchemaCache> findByConnectionId(UUID connectionId);
}
