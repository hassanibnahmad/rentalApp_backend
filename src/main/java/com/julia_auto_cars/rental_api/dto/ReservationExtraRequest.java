package com.julia_auto_cars.rental_api.dto;

import java.math.BigDecimal;

// DTO for reservation extra request
// This class represents the data structure for an extra item that can be added to a reservation, such as GPS, child seat, etc.
public record ReservationExtraRequest(
        String label,
        Integer quantity,
        BigDecimal unitPrice
) {}
