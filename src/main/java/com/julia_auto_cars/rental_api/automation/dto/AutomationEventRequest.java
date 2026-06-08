package com.julia_auto_cars.rental_api.automation.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Incoming event payload for {@code POST /api/automation/events}.
 * Optional HMAC verification is the caller's responsibility.
 */
public record AutomationEventRequest(
        @NotBlank String type,           // BOOKING_STARTED | BOOKING_CONFIRMED | RENTAL_UPCOMING | RENTAL_COMPLETED
        String reservationId,
        String source,                   // "webhook" | "polling" | "manual" — defaults to "webhook"
        Map<String, Object> metadata
) {}
