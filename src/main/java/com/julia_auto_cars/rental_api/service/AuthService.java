package com.julia_auto_cars.rental_api.service;

import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.julia_auto_cars.rental_api.dto.AuthRequest;
import com.julia_auto_cars.rental_api.dto.AuthResponse;
import com.julia_auto_cars.rental_api.dto.ForgotPasswordRequest;
import com.julia_auto_cars.rental_api.dto.ResetPasswordRequest;
import com.julia_auto_cars.rental_api.model.UserAccount;
import com.julia_auto_cars.rental_api.repository.UserAccountRepository;
import com.julia_auto_cars.rental_api.security.JwtTokenService;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;
    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResetService;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenService tokenService,
                       UserAccountRepository repository, PasswordEncoder passwordEncoder,
                       PasswordResetService passwordResetService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetService = passwordResetService;
    }

    public AuthResponse login(AuthRequest request) {
        String normalizedEmail = normalize(request.email());
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));
        var user = repository.findByEmailIgnoreCase(normalizedEmail).orElseThrow();
        var roles = user.getRoles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());
        String token = tokenService.generateToken(user.getEmail(), roles);
        Instant expiresAt = Instant.now().plusSeconds(3600);
        return new AuthResponse(token, expiresAt, user.getEmail(), user.getFullName(), roles);
    }

    public UserAccount seedAdmin(String email, String password, boolean forceReset) {
        String normalizedEmail = normalize(email);
        return repository.findByEmailIgnoreCase(normalizedEmail)
                .map(existing -> {
                    if (!forceReset) {
                        return existing;
                    }
                    String hashed = passwordEncoder.encode(password);
                    existing.setEmail(normalizedEmail);
                    existing.setPassword(hashed);
                    existing.setPasswordHash(hashed);
                    if (existing.getRoles() == null || existing.getRoles().isEmpty()) {
                        existing.setRoles(Set.of(com.julia_auto_cars.rental_api.model.UserRole.ADMIN));
                    }
                    if (existing.getFullName() == null || existing.getFullName().isBlank()) {
                        existing.setFullName("Fleet Admin");
                    }
                    return repository.save(existing);
                })
                .orElseGet(() -> {
                    var account = new UserAccount();
                    account.setEmail(normalizedEmail);
                    String hashed = passwordEncoder.encode(password);
                    account.setPassword(hashed);
                    account.setPasswordHash(hashed);
                    account.setFullName("Fleet Admin");
                    account.setRoles(Set.of(com.julia_auto_cars.rental_api.model.UserRole.ADMIN));
                    return repository.save(account);
                });
    }

    public void handleForgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = normalize(request.email());
        repository.findByEmailIgnoreCase(normalizedEmail)
                .ifPresentOrElse(user -> {
                    try {
                        passwordResetService.sendResetLink(user);
                    } catch (IllegalStateException mailFailure) {
                        LOGGER.warn("Password reset email could not be delivered to {}", normalizedEmail, mailFailure);
                    }
                }, () -> LOGGER.info("Password reset requested for unknown email: {}", normalizedEmail));
    }

    public void resetPassword(ResetPasswordRequest request) {
        var token = passwordResetService.requireValidToken(request.token());
        var account = token.getUser();
        String hashed = passwordEncoder.encode(request.newPassword());
        account.setPassword(hashed);
        account.setPasswordHash(hashed);
        repository.save(account);
        passwordResetService.markTokenUsed(token);
        LOGGER.info("Password successfully reset for {}", account.getEmail());
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}


