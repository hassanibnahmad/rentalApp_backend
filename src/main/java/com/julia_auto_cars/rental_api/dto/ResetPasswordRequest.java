package com.julia_auto_cars.rental_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 12, message = "Le mot de passe doit contenir au moins 12 caractères.") String newPassword) {}
