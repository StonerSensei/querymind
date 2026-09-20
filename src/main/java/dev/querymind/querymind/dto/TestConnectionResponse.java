package dev.querymind.querymind.dto;

/**
 * The result of POST /connections/{id}/test: did we manage to connect?
 */
public record TestConnectionResponse(boolean ok, String message) {
}
