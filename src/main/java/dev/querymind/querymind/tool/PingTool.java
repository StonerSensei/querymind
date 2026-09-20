package dev.querymind.querymind.tool;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * A very simple MCP tool used to check that the server works.
 *
 * Any MCP client (like Claude Desktop or the MCP Inspector) can call
 * these tools once it connects to the /mcp endpoint.
 *
 * These are just for Phase 0 testing. The real database tools
 * (list_tables, describe_table, run_query, explain_query) come later.
 */
@Component
public class PingTool {

    @McpTool(name = "ping", description = "Health check. Always returns 'pong'.")
    public String ping() {
        return "pong";
    }

    @McpTool(name = "echo", description = "Returns back the message you send. Useful for testing.")
    public String echo(
            @McpToolParam(description = "The text you want echoed back", required = true)
            String message) {
        return "You said: " + message;
    }
}
