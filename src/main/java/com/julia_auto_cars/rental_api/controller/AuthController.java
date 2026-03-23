package com.julia_auto_cars.rental_api.controller;


import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.julia_auto_cars.rental_api.dto.AuthRequest;
import com.julia_auto_cars.rental_api.dto.AuthResponse;
import com.julia_auto_cars.rental_api.dto.ForgotPasswordRequest;
import com.julia_auto_cars.rental_api.dto.ResetPasswordRequest;
import com.julia_auto_cars.rental_api.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/seed-admin")
    public ResponseEntity<Void> seed(@RequestParam String email,
                                     @RequestParam String password,
                                     @RequestParam(name = "force", defaultValue = "false") boolean force) {
        authService.seedAdmin(email, password, force);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        authService.handleForgotPassword(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}

