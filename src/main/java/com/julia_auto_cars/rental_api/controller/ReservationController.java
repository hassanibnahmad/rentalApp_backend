package com.julia_auto_cars.rental_api.controller;


import com.julia_auto_cars.rental_api.dto.ReservationRequest;
import com.julia_auto_cars.rental_api.dto.ReservationResponse;
import com.julia_auto_cars.rental_api.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(request));
    }

    @PutMapping("/{id}")
    public ReservationResponse update(@PathVariable Long id, @RequestBody ReservationRequest request) {
        return reservationService.updateReservation(id, request);
    }

    @PostMapping("/{id}/confirm")
    public ReservationResponse confirm(@PathVariable Long id) {
        return reservationService.confirmReservation(id);
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id, @RequestParam String reason) {
        return reservationService.cancelReservation(id, reason);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reservationService.deleteReservation(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<ReservationResponse> list() {
        return reservationService.listReservations();
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable Long id) {
        return reservationService.getReservation(id);
    }
}
