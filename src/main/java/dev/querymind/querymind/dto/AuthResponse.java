package dev.querymind.querymind.dto;

/**
 * What we send back after a successful register or login: the JWT token.
 */
public record AuthResponse(String token) {
}
