package com.julia_auto_cars.rental_api.controller;


import com.julia_auto_cars.rental_api.model.ReservationStatus;
import com.julia_auto_cars.rental_api.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// Controller to check the availability of cars for a given date range. It uses the ReservationRepository to find overlapping reservations based on car ID, date range, and reservation statuses (PENDING_PAYMENT and CONFIRMED). If there are no overlapping reservations, it returns true, indicating that the car is available for the specified dates.
@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final ReservationRepository reservationRepository;

    @GetMapping
    public boolean isAvailable(@RequestParam Long carId,
                               @RequestParam LocalDate start,
                               @RequestParam LocalDate end) {
        return reservationRepository.findOverlapping(
                carId,
                start,
                end,
                List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED)
        ).isEmpty();
    }
}
