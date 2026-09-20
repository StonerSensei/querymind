# Contributing to QueryMind

## Adding a new database adapter

QueryMind uses a pluggable adapter pattern to support different database
engines. PostgreSQL and MySQL are built in. Adding Oracle, SQLite, SQL Server,
or any other JDBC-compatible database takes four steps and touches no existing
code.

### How it works

Every database engine gets one class that implements `DbDialectAdapter`:

```
src/main/java/dev/querymind/querymind/dialect/
├── DbDialectAdapter.java     ← the interface (do not change)
├── PostgresAdapter.java      ← built-in
├── MySqlAdapter.java         ← built-in
└── YourNewAdapter.java       ← add yours here
```

Spring collects every `DbDialectAdapter` bean at startup and makes them all
available to `ConnectionService`. When a user registers a connection with
`dbType: "ORACLE"`, the service finds your adapter by matching the string
returned by `dbType()`. Nothing else in the app needs to change.

---

### Step 1 — add the JDBC driver to `pom.xml`

```xml
<!-- Oracle JDBC (example — adjust groupId/artifactId for your DB) -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc11</artifactId>
    <version>23.4.0.24.05</version>
    <scope>runtime</scope>
</dependency>
```

For databases whose driver is managed by the Spring Boot BOM (MySQL is one),
you can omit the `<version>` tag.

---

### Step 2 — implement `DbDialectAdapter`

Create a new file in `src/main/java/dev/querymind/querymind/dialect/`.
Annotate it `@Component` so Spring registers it automatically.

Here is a minimal Oracle example showing everything you need to fill in:

```java
package dev.querymind.querymind.dialect;

import org.springframework.stereotype.Component;

@Component
public class OracleAdapter implements DbDialectAdapter {

    /**
     * Must match the string the user passes as "dbType" when registering a
     * connection (case-insensitive — ConnectionService.getAdapter() uses
     * equalsIgnoreCase). Pick something short and obvious.
     */
    @Override
    public String dbType() {
        return "ORACLE";
    }

    /**
     * Build the JDBC URL from the three parts QueryMind stores per connection.
     * Oracle thin driver format: jdbc:oracle:thin:@host:port/serviceName
     * (databaseName maps to the service name for Oracle)
     */
    @Override
    public String jdbcUrl(String host, int port, String databaseName) {
        return "jdbc:oracle:thin:@" + host + ":" + port + "/" + databaseName;
    }

    /**
     * Return one row per table with exactly these two column aliases:
     *   table_name  VARCHAR
     *   row_count   BIGINT / NUMBER
     *
     * Oracle's ALL_TABLES.NUM_ROWS holds the last-analyzed estimate (fast,
     * no table scan). Filter to the current user's schema with USER_TABLES
     * if you want to avoid seeing system tables.
     */
    @Override
    public String listTablesSql() {
        return "SELECT table_name, num_rows AS row_count " +
               "FROM user_tables " +
               "ORDER BY table_name";
    }

    /**
     * Return one row per column with exactly these three column aliases:
     *   column_name  VARCHAR
     *   data_type    VARCHAR
     *   is_nullable  VARCHAR  — must be "YES" or "NO"
     *
     * The single "?" placeholder is filled with the table name by JdbcTemplate.
     * Oracle's ALL_COLUMNS uses NULLABLE ('Y'/'N') not IS_NULLABLE, so we
     * translate it with a CASE expression.
     */
    @Override
    public String describeTableSql() {
        return "SELECT column_name, " +
               "       data_type, " +
               "       CASE nullable WHEN 'Y' THEN 'YES' ELSE 'NO' END AS is_nullable " +
               "FROM   user_tab_columns " +
               "WHERE  table_name = UPPER(?) " +
               "ORDER BY column_id";
    }

    /**
     * Wrap the SELECT so the database returns its execution plan.
     * Oracle uses EXPLAIN PLAN SET STATEMENT_ID ... but that requires a
     * separate SELECT from PLAN_TABLE. The simplest cross-version approach
     * is to use the /*+ GATHER_PLAN_STATISTICS */ hint and fetch the plan
     * from V$SQL_PLAN — but for a first implementation, returning the
     * AUTOTRACE-style EXPLAIN PLAN output is fine:
     */
    @Override
    public String explainSql(String selectSql) {
        // Oracle does not support EXPLAIN in the same one-shot format as
        // PostgreSQL or MySQL. A minimal approach: prefix with EXPLAIN PLAN
        // and read from PLAN_TABLE. For a more useful result, consider using
        // the DBMS_XPLAN package instead.
        return "EXPLAIN PLAN FOR " + selectSql;
    }
}
```

---

### Step 3 — add sample data (optional but recommended)

If you want the adapter available in the local demo, add an init script under
`db/` and mount it in `docker-compose.yml`, following the pattern for MySQL:

```yaml
  oracle:
    image: container-registry.oracle.com/database/free:latest
    environment:
      ORACLE_PWD: querymind
    ports:
      - "1521:1521"
    volumes:
      - querymind_oracledata:/opt/oracle/oradata
      - ./db/oracle-init:/docker-entrypoint-initdb.d
```

And seed a demo connection in `DevDataSeeder.java`:

```java
createConnection(uid, "Oracle sample (read-only)",
        "ORACLE", "localhost", 1521, "FREEPDB1", true);
```

---

### Step 4 — register the connection via the REST API

With the app running, register a connection pointing at your database:

```bash
curl -s -X POST http://localhost:8085/connections \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name":         "My Oracle DB",
    "dbType":       "ORACLE",
    "host":         "localhost",
    "port":         1521,
    "databaseName": "FREEPDB1",
    "username":     "querymind",
    "password":     "querymind",
    "readOnly":     true
  }'
```

Test the connection:

```bash
curl -s -X POST http://localhost:8085/connections/$CID/test \
  -H "Authorization: Bearer $TOKEN"
```

Then use any of the four MCP tools with the returned `connectionId` — they
work identically across all adapters.

---

### Contract checklist

Before opening a pull request, verify your adapter satisfies these contracts.
The rest of the app depends on them.

| Method | What the rest of the app expects |
|---|---|
| `dbType()` | Short uppercase string; matches what users pass as `dbType` in the API |
| `jdbcUrl()` | Valid JDBC URL for the driver you added in Step 1 |
| `listTablesSql()` | Returns columns named exactly `table_name` (VARCHAR) and `row_count` (numeric) |
| `describeTableSql()` | Returns `column_name`, `data_type`, `is_nullable` ("YES"/"NO"); has exactly one `?` parameter (the table name) |
| `explainSql()` | Returns a SQL string that produces at least one row when executed via `JdbcTemplate.queryForList()` |

If `is_nullable` uses a different value than "YES" (Oracle uses "Y", for
example), translate it with a `CASE` expression in `describeTableSql()` as
shown above — `SchemaExtractor` and the column display code both check for
the exact string "YES".

---

## Other ways to contribute

- **Bug reports** — open an issue with the SQL that caused the problem and the
  error message from the audit log (`GET /audit`).
- **Safety validator improvements** — `SqlSafetyValidator.java` is the most
  security-sensitive file. Changes there need a full test matrix covering the
  cases in `PROJECT_PROGRESS.txt` section 8.
- **New sample data** — richer demo databases make the explain_query feature
  much more interesting to showcase. PRs that add indexes, foreign keys, and a
  few thousand rows to the sample schemas are very welcome.