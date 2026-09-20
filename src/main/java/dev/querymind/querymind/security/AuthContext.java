package dev.querymind.querymind.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * A small helper to answer the question "who is calling right now?".
 *
 * The JwtAuthFilter put the user id into the SecurityContext for this request.
 * Here we read it back out. Tools and services use this to know which user
 * they are working for.
 */
@Component
public class AuthContext {

    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UUID userId) {
            return userId;
        }
        throw new IllegalStateException("No logged-in user found on this request");
    }
}
