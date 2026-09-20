package dev.querymind.querymind.dto;

/**
 * One column of a table, as returned by the describe_table tool.
 */
public record ColumnInfo(String name, String type, boolean nullable) {
}
