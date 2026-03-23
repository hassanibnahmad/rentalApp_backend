package com.julia_auto_cars.rental_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@Email @NotBlank String email) {}
