# QueryMind

**Talk to any SQL database in plain English.**

QueryMind is an [MCP](https://modelcontextprotocol.io) server. It exposes a set
of database tools that any MCP-compatible AI client (Claude Desktop, and others)
can call. The AI writes the SQL, QueryMind runs it safely against your database,
and returns the results.

Think of it as a safe bridge between an AI assistant and your real databases.

---

## What it will do

- **Schema discovery** - the AI can ask "what tables do I have?" and "describe
  the orders table".
- **Safe query execution** - every AI-generated query is checked before it runs.
  Dangerous statements (DROP, TRUNCATE, DELETE without a WHERE) are blocked.
- **Query explanation** - ask "why is this query slow?" and get a plain-English
  answer with index suggestions.
- **Multi-database** - PostgreSQL and MySQL supported through pluggable adapters
  (Oracle can be added the same way).
- **Multi-tenant** - each user logs in and only sees their own database
  connections. One user can never touch another user's data.
- **Audit log** - every query (the SQL, timing, row count, and whether it passed
  or was blocked) is recorded.

---

## The four MCP tools (the goal)

| Tool | What it does | Status |
|------|--------------|--------|
| `list_tables` | Lists the tables in a connected database, with row counts. | Done |
| `describe_table` | Shows the columns, types and nullability of one table. | Done |
| `run_query` | Runs a query safely (with the safety checks below) and returns the rows. | Done |
| `explain_query` | Runs EXPLAIN ANALYZE and explains the plan in plain English. | Done |

> There are also two tiny test tools (`ping` and `echo`) that were used to prove
> the MCP wiring works.

---

## Architecture (high level)

```
Claude Desktop (or any MCP client)
        |
        |  MCP over HTTP (streamable), with a JWT token in the header
        v
QueryMind MCP Server  (Spring Boot)
        |
        |-- JWT check + ownership   --> who are you, and is this your DB?
        |-- SQL Safety Layer (JSqlParser)  --> checks every query
        |-- Dynamic DataSource (HikariCP)  --> connects to the user's DB
        |-- Audit log                      --> records what happened
        |
        v
System DB (PostgreSQL)   users, connections (encrypted), audit log
```

---

## Tech stack

- **Java 21**, **Spring Boot 4.1**
- **Spring AI 2.0** MCP server starter (`spring-ai-starter-mcp-server-webmvc`)
- **Spring Data JPA** + **PostgreSQL** for the system database
- **Spring Security** + **JWT** (jjwt) for login and multi-tenant isolation
- **JSqlParser** for the AST-based SQL safety checks
- **Jasypt** to encrypt stored database passwords
- **Anthropic Claude API** (optional) for plain-English query-plan explanations

---

## Getting started

You need: **Java 21**, **Docker**, and the Maven wrapper (`./mvnw`, included).

1. Start QueryMind's own database:

   ```bash
   docker compose up -d
   ```

2. Run the app:

   ```bash
   ./mvnw spring-boot:run
   ```

3. The server starts on **http://localhost:8085**. The MCP endpoint is
   `POST /mcp`.

See [command.md](command.md) for every useful command, including how to test the
MCP server with `curl`.

---

## Connecting Claude Desktop

QueryMind speaks standard MCP over HTTP. Claude Desktop connects to it using
[mcp-remote](https://github.com/geelen/mcp-remote), a small bridge that adds
the `Authorization` header that QueryMind requires.

**Step 1 — get a token.**

Start the app, then log in as the demo user:

```bash
TOKEN=$(curl -s -X POST http://localhost:8085/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@querymind.dev","password":"demo12345"}' \
  | sed 's/.*"token":"\([^"]*\)".*/\1/')
echo $TOKEN
```

**Step 2 — add QueryMind to Claude Desktop's config.**

Open Claude Desktop → Settings → Developer → Edit Config and paste the
following, replacing `YOUR_TOKEN_HERE` with the token you just copied:

```json
{
  "mcpServers": {
    "querymind": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "http://localhost:8085/mcp",
        "--header",
        "Authorization: Bearer YOUR_TOKEN_HERE"
      ]
    }
  }
}
```

A ready-to-edit copy of this config lives in
[`claude-desktop-config-example.json`](claude-desktop-config-example.json) in
the project root.

**Step 3 — restart Claude Desktop.**

The QueryMind tools (`list_tables`, `describe_table`, `run_query`,
`explain_query`) will appear in Claude Desktop's tool list. You can now ask
Claude things like:

> "What tables are in my database?" (pass the `connectionId` from the startup
> log, or from `GET /connections`)

> "Show me all orders over $300 from customers in the USA."

> "Why is this join query slow? `SELECT c.name, count(o.id) FROM customers c
> JOIN orders o ON c.id = o.customer_id GROUP BY c.name`"

**Token expiry.** Tokens last 24 hours by default. When yours expires, log in
again to get a new one and update the config.

**Deployed server.** If QueryMind is running on a remote host, replace
`http://localhost:8085` with its public URL. The rest of the steps are the
same.

---

## Authentication

Every request to `/mcp` needs a login token (JWT). You get one by registering or
logging in:

- `POST /auth/register` with `{ "email": ..., "password": ... }` -> returns a token
- `POST /auth/login` with the same body -> returns a token

Then send the token on every MCP call as a header:

```
Authorization: Bearer <token>
```

A request with no token (or a bad one) gets **401 Unauthorized**.

## Managing your connections

Once logged in, a user registers the databases they want the AI to query. These
are normal REST endpoints (all need the `Authorization: Bearer <token>` header):

- `POST /connections` - register a database. Body: `name`, `dbType`
  (`POSTGRES` or `MYSQL`), `host`, `port`, `databaseName`, `username`,
  `password`, `readOnly`. The password is encrypted before it is stored, and is
  **never** returned by any endpoint.
- `GET /connections` - list my connections (no passwords).
- `POST /connections/{id}/test` - actually open a connection to check the details
  are right. Returns `{ "ok": true/false, "message": ... }`.
- `DELETE /connections/{id}` - remove a connection (also closes its pool and
  clears its cached schema).

Because the dialect is pluggable, the same tools work whether a connection points
at **PostgreSQL** or **MySQL**.

To keep things fast, the table list for a connection is cached for 30 minutes in
the `schema_cache` table, so repeated `list_tables` calls don't hit
`information_schema` every time.

## The SQL safety layer

Before `run_query` runs anything, the SQL goes through a validator. It parses the
SQL into a syntax tree (using JSqlParser) and inspects what the statement *is* -
this is much safer than searching the text for scary words like "DROP", which can
hide in comments or column names.

The rules:

- **One statement at a time** - blocks tricks like `SELECT ...; DROP TABLE ...`.
- **SELECT is always allowed** (it only reads).
- **Read-only connections** reject everything except SELECT.
- **DELETE or UPDATE without a WHERE** is blocked (it would hit every row).
- **DROP / TRUNCATE / ALTER / CREATE** and anything else is blocked.

On top of the validator there are two more safety nets:

- **DB-level read-only.** A read-only connection's pool is opened read-only, so
  even if the validator had a bug the database driver itself would refuse writes.
- **Query timeout.** A single query is cancelled after 10 seconds so a slow query
  can't run forever.
- **Automatic row cap.** A SELECT with no `LIMIT` gets `LIMIT 500` added. The
  response says `resultsCapped: true` so the AI can tell the user there is more.

Every connection has a `readOnly` flag. Read-only is the safe default for letting
an AI explore a database.

## The audit log

Every `run_query` call is recorded in the `query_audit` table, whatever the
outcome:

- **SUCCESS** - with how long it took and how many rows came back.
- **BLOCKED** - with the reason the safety layer refused it.
- **ERROR** - with the database error message.

Each row also stores the SQL, the user, the connection, the time, and (if the
client sent it) the user's original plain-English question. So an admin can look
back and see exactly what the AI did and why.

Two design points:

- The audit write happens on a **background thread**, so it never slows down the
  query the user asked for.
- If the audit write ever fails, we just log a warning - an audit problem can
  never turn into an error the user sees.

A logged-in user can read their own history:

```
GET /audit    (with the Authorization: Bearer <token> header)
```

It returns that user's entries, newest first. One user never sees another user's
history.

## Explaining slow queries

The `explain_query` tool answers "why is this query slow?". It runs
`EXPLAIN ANALYZE` on the query and returns the database's execution plan.

- If an **Anthropic API key** is configured, the plan is sent to Claude, which
  returns a short plain-English explanation with concrete suggestions (like which
  index to add).
- If no key is set, the tool still returns the **raw query plan** with a short
  note - which is already useful on its own.

Only **SELECT** queries are allowed here, because `EXPLAIN ANALYZE` actually runs
the query and we never want that to run a write by accident.

To turn on the AI explanation, set the key in the environment before starting:

```bash
export ANTHROPIC_API_KEY="sk-ant-..."
```

## Trying the tools

To test the tools you need a database to point at. For convenience, a small
**sample database** (`sampledb`, with `customers` and `orders` tables) is created
automatically inside the Postgres container, and the app seeds a **demo user**
and two connections to it on first startup: one **read-only** and one
**writable** (so you can try the safety rules both ways).

When the app starts, look for these lines in the log:

```
Seeded a demo user and two database connections.
login email          = demo@querymind.dev
login password       = demo12345
read-only connection = <some-uuid>
writable connection  = <some-uuid>
```

Log in as that user to get a token, then pass a `connectionId` to the tools.
For example, `list_tables` returns:

```json
[{"name":"customers","rowCount":5},{"name":"orders","rowCount":8}]
```

And `run_query` with `SELECT * FROM customers` returns rows plus metadata:

```json
{"rows":[...],"rowCount":5,"executionMs":8,"resultsCapped":false,"rowLimit":null}
```

On the read-only connection, a `DELETE` or `DROP` is blocked. On the writable
connection, `DROP`/`TRUNCATE` and `DELETE`/`UPDATE` without a `WHERE` are still
blocked, but a normal `UPDATE ... WHERE ...` is allowed.

If a *different* user tries to use that `connectionId`, the tool returns an error
("No connection found ... for this user") - one user can never touch another
user's database.

The exact `curl` commands (login + tool calls) are in [command.md](command.md).

> Note: the sample database and the seeded demo user are for development only.
> In a real deployment, users register themselves and their own connections via
> the `/connections` API described above.

---

## Configuration

Set in `src/main/resources/application.properties`:

| Property | Meaning | Default |
|----------|---------|---------|
| `server.port` | Web server port | `8085` |
| `spring.datasource.url` | System DB URL | `jdbc:postgresql://localhost:5433/querymind` |
| `querymind.encryption.key` | Master key for encrypting DB passwords | `dev-secret-key-change-me` |
| `querymind.jwt.secret` | Secret used to sign login tokens (min 32 chars) | `dev-jwt-secret-...` |
| `querymind.jwt.expiration-ms` | How long a token is valid | `86400000` (24h) |
| `querymind.anthropic.api-key` | Anthropic key for AI explanations (optional) | empty (disabled) |
| `querymind.anthropic.model` | Claude model used by `explain_query` | `claude-3-5-sonnet-latest` |

In production, always override the secrets with environment variables:

```bash
export QUERYMIND_ENCRYPTION_KEY="a-long-random-secret"
export QUERYMIND_JWT_SECRET="another-long-random-secret-at-least-32-chars"
```

---

## System database tables

Created automatically on startup (Hibernate `ddl-auto=update`).

- **users** - people who use QueryMind.
- **db_connections** - databases a user registered (password stored encrypted).
- **query_audit** - a record of every query the AI ran.
- **schema_cache** - a saved copy of a database's table list (30-minute TTL), so
  `list_tables` doesn't hit `information_schema` on every call.

---

## Build progress

- [x] **Phase 0 - Foundation** - MCP server runs over HTTP, test tools
  (`ping`, `echo`) are registered and callable.
- [x] **Phase 1 - System database** - Postgres via Docker, JPA entities and
  tables, encrypted credential storage.
- [x] **Phase 2 - Schema discovery** - `list_tables` and `describe_table` tools,
  a pluggable dialect adapter (PostgreSQL), and a per-connection pool that builds
  a DataSource on demand from the stored (encrypted) credentials.
- [x] **Phase 3 - Auth and multi-tenant** - register/login with JWT (bcrypt
  passwords), `/mcp` locked down, and every tool call checks that the connection
  belongs to the logged-in user. One user cannot touch another's data.
- [x] **Phase 4 - run_query + safety layer** - AST-based validation (blocks DDL,
  multi-statements, and DELETE/UPDATE without WHERE), per-connection read-only
  enforced at two layers, a 10s query timeout, and automatic `LIMIT 500` with
  disclosure.
- [x] **Phase 5 - Audit log** - every `run_query` (SUCCESS/BLOCKED/ERROR) is
  recorded asynchronously with SQL, optional plain-English prompt, timing, row
  count, user and connection. `GET /audit` returns the caller's own history.
- [x] **Phase 6 - explain_query** - runs `EXPLAIN ANALYZE` on a SELECT and
  returns the plan, plus a plain-English explanation from Claude when an
  Anthropic API key is configured (graceful fallback to the raw plan otherwise).
- [x] **Phase 7 - Connection API + MySQL + caching** - a REST API to
  register/list/test/delete connections (passwords encrypted, never returned), a
  MySQL adapter proven end-to-end alongside PostgreSQL, and a 30-minute schema
  cache behind `list_tables`.
- [ ] **Phase 8** - Polish, demo, deploy.