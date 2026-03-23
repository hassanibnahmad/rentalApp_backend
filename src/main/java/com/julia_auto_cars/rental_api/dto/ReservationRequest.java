package com.julia_auto_cars.rental_api.dto;

import java.time.LocalDate;
import java.util.List;
// DTO for creating a new reservation
// - it captures all the necessary information needed to create a reservation, including customer details, rental period, and any additional extras they may want to include with their reservation.
// - This DTO is used when a client sends a request to create a new reservation, ensuring that all required data is provided in a structured format.
// Record class in Java is a special type of class that is designed to be a simple carrier for immutable data. It automatically generates boilerplate code such as constructors, getters, equals(), hashCode(), and toString() methods based on the fields defined in the record. This makes it an ideal choice for DTOs (Data Transfer Objects) that are primarily used to transfer data between different layers of an application without any additional behavior or logic.
// In this case, the ReservationRequest record captures all the necessary information needed to create a reservation, including customer details, rental period, and any additional extras they may want to include with their reservation. This DTO is used when a client sends a request to create a new reservation, ensuring that all required data is provided in a structured format.
// the deference between a record and a regular class is that a record is immutable and automatically generates boilerplate code, while a regular class can be mutable and requires manual implementation of constructors, getters, equals(), hashCode(), and toString() methods. Records are ideal for simple data carriers like DTOs, while regular classes are more suitable for complex objects with behavior and logic.
public record ReservationRequest(
        Long carId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String documentId,
        String pickupCity,
        LocalDate pickupDate,
        String returnCity,
        LocalDate returnDate,
        String notes,
        List<ReservationExtraRequest> extras
) {}
