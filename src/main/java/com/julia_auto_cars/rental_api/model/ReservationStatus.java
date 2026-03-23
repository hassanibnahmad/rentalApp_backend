package com.julia_auto_cars.rental_api.model;

// Enum to represent the status of a reservation,
// why ENUM? Because it provides a fixed set of constants, making the code more readable and less error-prone when dealing with reservation statuses.
public enum ReservationStatus {
    DRAFT,
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}