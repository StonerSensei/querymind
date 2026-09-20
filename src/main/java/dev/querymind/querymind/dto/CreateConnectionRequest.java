package dev.querymind.querymind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * The body of POST /connections - the details a user gives to register a
 * database they want the AI to be able to query.
 *
 * The password comes in as plain text here; we encrypt it before saving and
 * never send it back out again.
 */
public record CreateConnectionRequest(
        @NotBlank String name,
        @NotBlank String dbType,
        @NotBlank String host,
        @Positive int port,
        @NotBlank String databaseName,
        @NotBlank String username,
        @NotBlank String password,
        boolean readOnly) {
}
