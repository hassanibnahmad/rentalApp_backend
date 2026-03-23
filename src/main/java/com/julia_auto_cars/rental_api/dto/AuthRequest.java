package com.julia_auto_cars.rental_api.dto;

// DTO for authentication requests (login), record-like for immutability and simplicity
public record AuthRequest(String email, String password) {}

