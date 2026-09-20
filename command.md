# QueryMind - Useful Commands

All the commands you need while working on this project. Run them from the
project root unless noted otherwise.

## Database (QueryMind's own Postgres)

Start the database (runs in the background):

```bash
docker compose up -d
```

Stop the database:

```bash
docker compose down
```

Stop the database AND delete all its data:

```bash
docker compose down -v
```

Open a psql shell inside the database container:

```bash
docker exec -it querymind-postgres psql -U querymind -d querymind
```

List the tables:

```bash
docker exec querymind-postgres psql -U querymind -d querymind -c "\dt"
```

> Note: the database is published on host port **5433** (not the usual 5432),
> because 5432 was already taken on this machine. Inside the container it is
> still 5432.

### Sample database (for testing the tools)

A second database called `sampledb` (tables `customers` and `orders`) is created
automatically for testing. See its tables:

```bash
docker exec querymind-postgres psql -U querymind -d sampledb -c "\dt"
```

Reset the sample data from scratch (drops and recreates everything, then the app
will seed fresh connections on its next start):

```bash
docker compose down -v
docker compose up -d
```

There is also a **MySQL** sample database (table `products`) in a separate
container, to show the tools work across engines. It is on host port **3307**
(user/password `querymind`/`querymind`, database `sampledb`). See its tables:

```bash
docker exec querymind-mysql mysql -uquerymind -pquerymind sampledb -e "SHOW TABLES;"
```

## Build and run the app

Compile the code:

```bash
./mvnw compile
```

Run the app (needs the database running first):

```bash
./mvnw spring-boot:run
```

Build a runnable jar:

```bash
./mvnw clean package
```

Run the jar:

```bash
java -jar target/querymind-0.0.1-SNAPSHOT.jar
```

The app starts on **http://localhost:8085** (8080 was already in use).

## Log in first (get a token)

Every `/mcp` call needs a login token. The app seeds a demo user on first start
(`demo@querymind.dev` / `demo12345`). Log in and save the token to a variable:

```bash
TOKEN=$(curl -s -X POST http://localhost:8085/auth/login -H "Content-Type: application/json" -d '{"email":"demo@querymind.dev","password":"demo12345"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
```

Register a brand new user instead (also returns a token):

```bash
curl -s -X POST http://localhost:8085/auth/register -H "Content-Type: application/json" -d '{"email":"me@example.com","password":"mypassword"}'
```

## Manage connections (REST API)

Register a database connection. The response has no password. Save the new id
into `CID` so the tool commands below can use it:

```bash
CONN=$(curl -s -X POST http://localhost:8085/connections -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"name":"My MySQL","dbType":"MYSQL","host":"localhost","port":3307,"databaseName":"sampledb","username":"querymind","password":"querymind","readOnly":true}')
echo "$CONN"
CID=$(echo "$CONN" | sed 's/.*"id":"\([^"]*\)".*/\1/')
```

(For a PostgreSQL connection use `"dbType":"POSTGRES"`, `"port":5433`.)

List my connections (passwords are never returned):

```bash
curl -s http://localhost:8085/connections -H "Authorization: Bearer $TOKEN"
```

Test that a connection actually works:

```bash
curl -s -X POST http://localhost:8085/connections/$CID/test -H "Authorization: Bearer $TOKEN"
```

Delete a connection (also closes its pool and clears its cached schema):

```bash
curl -s -X DELETE http://localhost:8085/connections/$CID -H "Authorization: Bearer $TOKEN"
```

## Test the MCP server by hand (curl)

The MCP endpoint is `POST /mcp`. You must:
1. log in and get a `$TOKEN` (above),
2. call `initialize` first (with the token),
3. read the `Mcp-Session-Id` response header,
4. send both the token and that session id on every later call.

Step 1 - initialize and save the response headers:

```bash
curl -s -D /tmp/init_headers.txt -X POST http://localhost:8085/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -H "Authorization: Bearer $TOKEN" -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'
```

Step 2 - grab the session id into a shell variable:

```bash
SID=$(grep -i "Mcp-Session-Id" /tmp/init_headers.txt | awk '{print $2}' | tr -d '\r')
```

Step 3 - list the tools:

```bash
curl -s -X POST http://localhost:8085/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -H "Authorization: Bearer $TOKEN" -H "Mcp-Session-Id: $SID" -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```

Step 4 - call the ping tool:

```bash
curl -s -X POST http://localhost:8085/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -H "Authorization: Bearer $TOKEN" -H "Mcp-Session-Id: $SID" -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"ping","arguments":{}}}'
```

> Tip: leave off the `Authorization` header and you'll get HTTP 401 - that's the
> auth layer working.

## Test the schema tools (list_tables / describe_table)

These tools need a `connectionId`. When the app starts it seeds a demo user and
two connections (read-only and writable) and prints the details in the log:

```
login email          = demo@querymind.dev
login password       = demo12345
read-only connection = <some-uuid>
writable connection  = <some-uuid>
```

Save an id into a variable (do the login + `initialize` + session-id steps above first):

```bash
CID=<paste-a-connectionId-here>
```

List the tables in the connected database:

```bash
curl -s -X POST http://localhost:8085/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -H "Authorization: Bearer $TOKEN" -H "Mcp-Session-Id: $SID" -d "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"list_tables\",\"arguments\":{\"connectionId\":\"$CID\"}}}"
```

Describe a single table:

```bash
curl -s -X POST http://localhost:8085/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -H "Authorization: Bearer $TOKEN" -H "Mcp-Session-Id: $SID" -d "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"describe_table\",\"arguments\":{\"connectionId\":\"$CID\",\"tableName\":\"orders\"}}}"
```

## Test run_query (with the safety layer)

Run a SELECT (returns rows + metadata like resultsCapped):

```bash
curl -s -X POST http://localhost:8085/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -H "Authorization: Bearer $TOKEN" -H "Mcp-Session-Id: $SID" -d "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"run_query\",\"arguments\":{\"connectionId\":\"$CID\",\"sql\":\"SELECT * FROM customers\"}}}"
```

See the automatic row cap in action (1000 rows requested, 500 returned,
`resultsCapped:true`):

```bash
curl -s -X POST http://localhost:8085/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -H "Authorization: Bearer $TOKEN" -H "Mcp-Session-Id: $SID" -d "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{\"name\":\"run_query\",\"arguments\":{\"connectionId\":\"$CID\",\"sql\":\"SELECT * FROM generate_series(1,1000) AS n\"}}}"
```

See a dangerous query get blocked (returns an error, nothing runs):

```bash
curl -s -X POST http://localhost:8085/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -H "Authorization: Bearer $TOKEN" -H "Mcp-Session-Id: $SID" -d "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"run_query\",\"arguments\":{\"connectionId\":\"$CID\",\"sql\":\"DROP TABLE customers\"}}}"
```

`run_query` also accepts an optional `prompt` argument - the user's original
question in plain English - which gets stored in the audit log:

```bash
curl -s -X POST http://localhost:8085/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -H "Authorization: Bearer $TOKEN" -H "Mcp-Session-Id: $SID" -d "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\",\"params\":{\"name\":\"run_query\",\"arguments\":{\"connectionId\":\"$CID\",\"sql\":\"SELECT * FROM customers\",\"prompt\":\"Show me all customers\"}}}"
```

## Test explain_query (why is this query slow?)

Runs `EXPLAIN ANALYZE` and returns the plan. Without an Anthropic API key it
returns the raw plan; with one it returns a plain-English explanation:

```bash
curl -s -X POST http://localhost:8085/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -H "Authorization: Bearer $TOKEN" -H "Mcp-Session-Id: $SID" -d "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\",\"params\":{\"name\":\"explain_query\",\"arguments\":{\"connectionId\":\"$CID\",\"sql\":\"SELECT c.name, count(o.id) FROM customers c JOIN orders o ON c.id = o.customer_id GROUP BY c.name\"}}}"
```

To enable the AI explanation, set your key before starting the app:

```bash
export ANTHROPIC_API_KEY="sk-ant-..."
```

Only SELECT queries are allowed - explaining a DROP/INSERT/etc. is blocked.

## View the audit log

Every query you run is recorded. See your own history (newest first). This is a
normal REST endpoint, so it only needs the token (no MCP session):

```bash
curl -s http://localhost:8085/audit -H "Authorization: Bearer $TOKEN"
```

## Health check

```bash
curl http://localhost:8085/actuator/health
```
