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
 * A database that a user registered so the AI agent can query it.
 *
 * The password is stored encrypted (see CredentialEncryptor), never in
 * plain text.
 */
@Entity
@Table(name = "db_connections")
@Getter
@Setter
public class DbConnection {

    @Id
    @UuidGenerator
    private UUID id;

    // Which user owns this connection.
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // A friendly name, e.g. "My prod DB".
    @Column(nullable = false)
    private String name;

    // POSTGRES, MYSQL or ORACLE.
    @Column(name = "db_type", nullable = false)
    private String dbType;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(name = "database_name", nullable = false)
    private String databaseName;

    @Column(nullable = false)
    private String username;

    // Encrypted password.
    @Column(name = "password_enc", nullable = false, columnDefinition = "text")
    private String passwordEnc;

    // When true, the agent is only allowed to read from this database.
    @Column(name = "read_only", nullable = false)
    private boolean readOnly = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
