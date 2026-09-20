package dev.querymind.querymind.config;

import dev.querymind.querymind.model.DbConnection;
import dev.querymind.querymind.model.User;
import dev.querymind.querymind.repository.DbConnectionRepository;
import dev.querymind.querymind.repository.UserRepository;
import dev.querymind.querymind.security.CredentialEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a demo user and demo database connections on first startup so the app
 * is immediately usable without any manual setup.
 *
 * Three connections are created:
 *   - PostgreSQL read-only  (safe default; shows the safety layer blocking writes)
 *   - PostgreSQL writable   (shows UPDATE/DELETE working within the safety rules)
 *   - MySQL read-only       (shows the multi-engine adapter story)
 *
 * The guard checks whether the demo user already exists, not whether ANY
 * connection exists. This means:
 *   - If a real user registers their own connection and the app restarts, we
 *     still re-seed the demo user correctly.
 *   - If the demo user already exists (e.g. after a hot reload), we skip
 *     seeding so we don't create duplicate connections.
 */
@ConditionalOnProperty(name = "querymind.seed.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private static final String DEMO_EMAIL    = "demo@querymind.dev";
    private static final String DEMO_PASSWORD = "demo12345";

    private final UserRepository userRepository;
    private final DbConnectionRepository connectionRepository;
    private final CredentialEncryptor encryptor;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(UserRepository userRepository,
                         DbConnectionRepository connectionRepository,
                         CredentialEncryptor encryptor,
                         PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.connectionRepository = connectionRepository;
        this.encryptor = encryptor;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Guard on the demo user, not on total connection count.
        // If a real user has registered their own connections and the app
        // restarts, we still need to seed the demo user fresh.
        if (userRepository.findByEmail(DEMO_EMAIL).isPresent()) {
            return;
        }

        User demo = new User();
        demo.setEmail(DEMO_EMAIL);
        demo.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        userRepository.save(demo);

        java.util.UUID uid = demo.getId();

        // PostgreSQL sample database (customers + orders tables).
        DbConnection pgReadOnly = createConnection(uid, "PostgreSQL sample (read-only)",
                "POSTGRES", "localhost", 5433, "sampledb", true);
        DbConnection pgWritable = createConnection(uid, "PostgreSQL sample (writable)",
                "POSTGRES", "localhost", 5433, "sampledb", false);

        // MySQL sample database (products table) — demonstrates the pluggable
        // dialect adapter: the same four MCP tools work across both engines.
        DbConnection myReadOnly = createConnection(uid, "MySQL sample (read-only)",
                "MYSQL", "localhost", 3307, "sampledb", true);

        log.info("=====================================================");
        log.info("Seeded demo user and three database connections.");
        log.info("login email              = {}", DEMO_EMAIL);
        log.info("login password           = {}", DEMO_PASSWORD);
        log.info("postgres read-only  id   = {}", pgReadOnly.getId());
        log.info("postgres writable   id   = {}", pgWritable.getId());
        log.info("mysql    read-only  id   = {}", myReadOnly.getId());
        log.info("Log in to get a token, then pass a connectionId to the tools.");
        log.info("=====================================================");
    }

    private DbConnection createConnection(java.util.UUID userId, String name,
                                          String dbType, String host, int port,
                                          String database, boolean readOnly) {
        DbConnection conn = new DbConnection();
        conn.setUserId(userId);
        conn.setName(name);
        conn.setDbType(dbType);
        conn.setHost(host);
        conn.setPort(port);
        conn.setDatabaseName(database);
        conn.setUsername("querymind");
        conn.setPasswordEnc(encryptor.encrypt("querymind"));
        conn.setReadOnly(readOnly);
        connectionRepository.save(conn);
        return conn;
    }
}