package com.julia_auto_cars.rental_api.automation.dto;

import java.util.List;
import java.util.UUID;

public record AutomationEventResponse(
        UUID eventId,
        List<String> flowsTriggered,
        boolean duplicate
) {}
