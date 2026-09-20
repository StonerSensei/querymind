package dev.querymind.querymind.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.querymind.querymind.model.DbConnection;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one connection pool per registered database.
 *
 * Opening a brand new connection for every query would be slow, so we build
 * a small pool the first time a database is used and keep it in memory for
 * next time. The pools are stored in a map keyed by the connection id.
 */
@Component
public class DataSourcePool {

    private final Map<UUID, HikariDataSource> pools = new ConcurrentHashMap<>();

    // Return the existing pool for this connection, or build one if we don't
    // have it yet.
    public DataSource getOrCreate(DbConnection conn, String jdbcUrl, String password) {
        return pools.computeIfAbsent(conn.getId(), id -> build(conn, jdbcUrl, password));
    }

    private HikariDataSource build(DbConnection conn, String jdbcUrl, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(conn.getUsername());
        config.setPassword(password);
        // Keep the pool small - this is a side project, not high traffic.
        config.setMaximumPoolSize(2);
        config.setPoolName("pool-" + conn.getName());
        // Second layer of protection: if the connection is read-only, tell the
        // driver too. Even if the SQL validator had a bug, the database itself
        // would refuse any write on these connections.
        config.setReadOnly(conn.isReadOnly());
        return new HikariDataSource(config);
    }

    // Close a pool and remove it. Used later when a user deletes a connection.
    public void evict(UUID connectionId) {
        HikariDataSource ds = pools.remove(connectionId);
        if (ds != null) {
            ds.close();
        }
    }
}
