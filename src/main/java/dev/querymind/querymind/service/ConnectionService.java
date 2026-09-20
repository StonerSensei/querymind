package dev.querymind.querymind.service;

import dev.querymind.querymind.datasource.DataSourcePool;
import dev.querymind.querymind.dialect.DbDialectAdapter;
import dev.querymind.querymind.dto.CreateConnectionRequest;
import dev.querymind.querymind.dto.TestConnectionResponse;
import dev.querymind.querymind.model.DbConnection;
import dev.querymind.querymind.repository.DbConnectionRepository;
import dev.querymind.querymind.security.CredentialEncryptor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

/**
 * Everything to do with a registered database connection:
 * registering, listing, deleting and testing them, finding one for a user,
 * choosing the right dialect adapter, and giving back a ready-to-use DataSource
 * (with the password decrypted).
 */
@Service
public class ConnectionService {

    private final DbConnectionRepository connectionRepository;
    private final CredentialEncryptor encryptor;
    private final DataSourcePool dataSourcePool;
    private final SchemaCacheService schemaCacheService;

    // Spring gives us every DbDialectAdapter bean in this list.
    private final List<DbDialectAdapter> adapters;

    public ConnectionService(DbConnectionRepository connectionRepository,
                             CredentialEncryptor encryptor,
                             DataSourcePool dataSourcePool,
                             SchemaCacheService schemaCacheService,
                             List<DbDialectAdapter> adapters) {
        this.connectionRepository = connectionRepository;
        this.encryptor = encryptor;
        this.dataSourcePool = dataSourcePool;
        this.schemaCacheService = schemaCacheService;
        this.adapters = adapters;
    }

    // Register a new database connection for a user. The password is encrypted
    // before it is saved.
    public DbConnection registerConnection(UUID userId, CreateConnectionRequest request) {
        // Make sure we actually support this database type before saving.
        getAdapter(request.dbType());

        DbConnection conn = new DbConnection();
        conn.setUserId(userId);
        conn.setName(request.name());
        conn.setDbType(request.dbType().toUpperCase());
        conn.setHost(request.host());
        conn.setPort(request.port());
        conn.setDatabaseName(request.databaseName());
        conn.setUsername(request.username());
        conn.setPasswordEnc(encryptor.encrypt(request.password()));
        conn.setReadOnly(request.readOnly());
        return connectionRepository.save(conn);
    }

    // All connections that belong to a user.
    public List<DbConnection> listConnections(UUID userId) {
        return connectionRepository.findByUserId(userId);
    }

    // Delete a user's connection and clean up its pool and cached schema.
    public void deleteConnection(UUID connectionId, UUID userId) {
        DbConnection conn = findConnection(connectionId, userId); // ownership check
        connectionRepository.delete(conn);
        dataSourcePool.evict(connectionId);
        schemaCacheService.evict(connectionId);
    }

    // Try to actually connect, so a user can check their details are right.
    public TestConnectionResponse testConnection(UUID connectionId, UUID userId) {
        DbConnection conn = findConnection(connectionId, userId);
        try {
            DataSource dataSource = getDataSource(conn);
            try (Connection c = dataSource.getConnection()) {
                boolean ok = c.isValid(5);
                return new TestConnectionResponse(ok,
                        ok ? "Connection successful" : "The database did not confirm the connection");
            }
        } catch (Exception e) {
            // Don't keep a broken pool or stale schema cache around after a
            // failed test — both need to be rebuilt on the next attempt.
            dataSourcePool.evict(connectionId);
            schemaCacheService.evict(connectionId);
            return new TestConnectionResponse(false, "Could not connect: " + e.getMessage());
        }
    }

    // Look up a saved connection by its id, but only if it belongs to this user.
    // This is the tenant-isolation check: if the connection is someone else's,
    // we treat it as "not found" so users can't even tell it exists.
    public DbConnection findConnection(UUID connectionId, UUID userId) {
        return connectionRepository.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No connection found with id " + connectionId + " for this user"));
    }

    // Find the adapter that matches this database type (POSTGRES, MYSQL, ...).
    public DbDialectAdapter getAdapter(String dbType) {
        for (DbDialectAdapter adapter : adapters) {
            if (adapter.dbType().equalsIgnoreCase(dbType)) {
                return adapter;
            }
        }
        throw new IllegalArgumentException("No adapter for database type: " + dbType);
    }

    // Build (or reuse) a connection pool for this database and return it.
    public DataSource getDataSource(DbConnection conn) {
        DbDialectAdapter adapter = getAdapter(conn.getDbType());
        String jdbcUrl = adapter.jdbcUrl(conn.getHost(), conn.getPort(), conn.getDatabaseName());
        String password = encryptor.decrypt(conn.getPasswordEnc());
        return dataSourcePool.getOrCreate(conn, jdbcUrl, password);
    }
}