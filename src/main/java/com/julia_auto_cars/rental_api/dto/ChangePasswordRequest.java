package com.julia_auto_cars.rental_api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String email,
        @NotBlank String currentPassword,
        @NotBlank String newPassword) {}
