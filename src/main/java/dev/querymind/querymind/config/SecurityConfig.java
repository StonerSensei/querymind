package dev.querymind.querymind.config;

import dev.querymind.querymind.security.JwtAuthFilter;
import dev.querymind.querymind.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security setup.
 *
 * Rules:
 *  - /auth/** (register, login) is open to everyone.
 *  - the health check is open.
 *  - everything else (including the /mcp tools) needs a valid JWT token.
 *
 * We are a token-based API, so there are no server-side sessions.
 */
@Configuration
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // No sessions - every request must carry its own token.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Allow the built-in error page so failures show the
                        // real status code (e.g. 400) instead of a stray 401.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                // If a request has no valid token, return 401 (not logged in).
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, e) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                // Check the token before the normal username/password step.
                .addFilterBefore(new JwtAuthFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Used to hash passwords when registering and to check them at login.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
