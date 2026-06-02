package com.julia_auto_cars.rental_api.dto;


import com.julia_auto_cars.rental_api.model.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// DTO for reservation response
// This class represents the data structure for the response of a reservation, containing all relevant information about
public record ReservationResponse(
        Long id,
        Long carId,
        ReservationStatus status,
        String customerFirstName,
        String customerLastName,
        String customerEmail,
        String customerPhone,
        String documentId,
        String pickupCity,
        LocalDate pickupDate,
        String returnCity,
        LocalDate returnDate,
        BigDecimal totalAmount,
        List<String> extras,
        String notes
) {}