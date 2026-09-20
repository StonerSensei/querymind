package dev.querymind.querymind.tool;

import dev.querymind.querymind.dialect.DbDialectAdapter;
import dev.querymind.querymind.dto.ColumnInfo;
import dev.querymind.querymind.dto.TableInfo;
import dev.querymind.querymind.model.DbConnection;
import dev.querymind.querymind.security.AuthContext;
import dev.querymind.querymind.service.ConnectionService;
import dev.querymind.querymind.service.SchemaCacheService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

/**
 * The schema discovery tools that an AI client can call over MCP.
 *
 * The AI usually calls list_tables first to see what is in the database,
 * then describe_table to learn the columns of a table before writing a query.
 *
 * Every call is tied to the logged-in user, so a user only ever sees their
 * own databases.
 */
@Component
public class SchemaTools {

    private final ConnectionService connectionService;
    private final SchemaCacheService schemaCacheService;
    private final AuthContext authContext;

    public SchemaTools(ConnectionService connectionService,
                       SchemaCacheService schemaCacheService,
                       AuthContext authContext) {
        this.connectionService = connectionService;
        this.schemaCacheService = schemaCacheService;
        this.authContext = authContext;
    }

    @McpTool(name = "list_tables",
            description = "Lists the tables in a registered database, with an approximate row count for each.")
    public List<TableInfo> listTables(
            @McpToolParam(description = "The id of the registered database connection", required = true)
            String connectionId) {

        UUID userId = authContext.getCurrentUserId();
        DbConnection conn = connectionService.findConnection(ToolUtils.parseConnectionId(connectionId), userId);

        // Serve from the 30-minute schema cache if we can; otherwise read the DB.
        return schemaCacheService.getTables(conn.getId(), () -> loadTablesFromDb(conn));
    }

    // Actually read the table list from the database.
    private List<TableInfo> loadTablesFromDb(DbConnection conn) {
        DbDialectAdapter adapter = connectionService.getAdapter(conn.getDbType());
        DataSource dataSource = connectionService.getDataSource(conn);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return jdbc.query(adapter.listTablesSql(), (rs, rowNum) ->
                new TableInfo(rs.getString("table_name"), rs.getLong("row_count")));
    }

    @McpTool(name = "describe_table",
            description = "Shows the columns of a table: name, data type and whether it can be null.")
    public List<ColumnInfo> describeTable(
            @McpToolParam(description = "The id of the registered database connection", required = true)
            String connectionId,
            @McpToolParam(description = "The name of the table to describe", required = true)
            String tableName) {

        UUID userId = authContext.getCurrentUserId();
        DbConnection conn = connectionService.findConnection(ToolUtils.parseConnectionId(connectionId), userId);
        DbDialectAdapter adapter = connectionService.getAdapter(conn.getDbType());
        DataSource dataSource = connectionService.getDataSource(conn);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // The tableName is passed in safely as a query parameter (the "?").
        return jdbc.query(adapter.describeTableSql(), (rs, rowNum) ->
                        new ColumnInfo(
                                rs.getString("column_name"),
                                rs.getString("data_type"),
                                "YES".equals(rs.getString("is_nullable"))),
                tableName);
    }
}