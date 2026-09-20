package dev.querymind.querymind.controller;

import dev.querymind.querymind.dto.AuthResponse;
import dev.querymind.querymind.dto.LoginRequest;
import dev.querymind.querymind.dto.RegisterRequest;
import dev.querymind.querymind.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The login and registration endpoints. These are open to everyone (you don't
 * need a token to get a token).
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        String token = authService.register(request.email(), request.password());
        return new AuthResponse(token);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.email(), request.password());
        return new AuthResponse(token);
    }
}
