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
 * A saved copy of a database's schema (tables and columns) as JSON.
 *
 * Reading the schema from a database on every question is slow, so we
 * cache it here and refresh it now and then. Schemas rarely change.
 */
@Entity
@Table(name = "schema_cache")
@Getter
@Setter
public class SchemaCache {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    // The tables and columns saved as JSON text.
    @Column(name = "schema_json", nullable = false, columnDefinition = "text")
    private String schemaJson;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt = Instant.now();
}
