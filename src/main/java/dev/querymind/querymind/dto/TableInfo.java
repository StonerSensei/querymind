package dev.querymind.querymind.dto;

/**
 * One table in a database, as returned by the list_tables tool.
 *
 * rowCount is approximate (databases keep a rough count that is fast to read).
 */
public record TableInfo(String name, long rowCount) {
}
