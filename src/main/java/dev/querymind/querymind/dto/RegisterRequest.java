package dev.querymind.querymind.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * The body of POST /auth/register.
 */
public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank String password) {
}
