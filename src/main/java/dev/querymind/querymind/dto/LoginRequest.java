package dev.querymind.querymind.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The body of POST /auth/login.
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password) {
}
