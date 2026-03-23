package com.julia_auto_cars.rental_api.dto;

import java.time.Instant;
import java.util.Set;
//returned to client after successful authentication, contains JWT token and user info, record-like for immutability and simplicity

public record AuthResponse(String token, Instant expiresAt, String email, String fullName, Set<String> roles) {}

