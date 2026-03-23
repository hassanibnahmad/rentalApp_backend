package com.julia_auto_cars.rental_api.service;

import com.julia_auto_cars.rental_api.dto.ReservationRequest;
import com.julia_auto_cars.rental_api.dto.ReservationResponse;

import java.util.List;

public interface ReservationService {
    ReservationResponse createReservation(ReservationRequest request);
    ReservationResponse confirmReservation(Long reservationId);
    ReservationResponse cancelReservation(Long reservationId, String reason);
    void deleteReservation(Long reservationId);
    List<ReservationResponse> listReservations();
    ReservationResponse getReservation(Long reservationId);
}