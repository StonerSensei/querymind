package dev.querymind.querymind.tool;

import java.util.UUID;

/**
 * Small helpers shared by all MCP tool classes.
 */
class ToolUtils {

    private ToolUtils() {}

    /**
     * Parse a connection ID string into a UUID, returning a clean error message
     * if the format is wrong. Without this, UUID.fromString() throws an
     * IllegalArgumentException with a raw Java message that surfaces directly
     * to the MCP client.
     */
    static UUID parseConnectionId(String connectionId) {
        try {
            return UUID.fromString(connectionId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid connection ID format: \"" + connectionId
                    + "\". Expected a UUID like 550e8400-e29b-41d4-a716-446655440000.");
        }
    }
}