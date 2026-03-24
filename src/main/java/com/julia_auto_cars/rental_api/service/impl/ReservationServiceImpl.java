package com.julia_auto_cars.rental_api.service.impl;

import com.julia_auto_cars.rental_api.dto.ReservationExtraRequest;
import com.julia_auto_cars.rental_api.dto.ReservationRequest;
import com.julia_auto_cars.rental_api.dto.ReservationResponse;
import com.julia_auto_cars.rental_api.model.*;
import com.julia_auto_cars.rental_api.repository.CarRepository;
import com.julia_auto_cars.rental_api.repository.CustomerRepository;
import com.julia_auto_cars.rental_api.repository.ReservationRepository;
import com.julia_auto_cars.rental_api.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final CustomerRepository customerRepository;
    private final CarRepository carRepository;

    // This method is used to create a new reservation. It validates the input data, checks for availability, saves the customer if necessary, creates the reservation, attaches any extras, computes the totals, and saves the reservation to the repository. Finally, it maps the saved reservation to a response DTO and returns it.
    @Override
    public ReservationResponse createReservation(ReservationRequest request) {
        validateDates(request);
        ensureAvailability(request);

        Car car = carRepository.findById(request.carId())
            .orElseThrow(() -> new IllegalArgumentException("Véhicule introuvable"));
        Integer pricePerDay = car.getPricePerDay();
        if (pricePerDay == null) {
            throw new IllegalStateException("Tarif journalier non défini pour ce véhicule");
        }

        Customer customer = customerRepository
                .findByEmail(request.email())
                .orElseGet(() -> saveCustomer(request));

        Reservation reservation = Reservation.builder()
                .carId(request.carId())
                .customer(customer)
                .pickupCity(request.pickupCity())
                .pickupDate(request.pickupDate())
                .returnCity(request.returnCity())
                .returnDate(request.returnDate())
                .status(ReservationStatus.PENDING_PAYMENT)
            .dailyRate(BigDecimal.valueOf(pricePerDay))
                .daysCount(daysBetween(request))
                .totalAmount(BigDecimal.ZERO)
                .notes(request.notes())
                .build();

        attachExtras(reservation, request.extras());
        computeTotals(reservation);

        Reservation saved = reservationRepository.save(reservation);
        return mapToResponse(saved);
    }

    // This method is used to confirm a reservation. It retrieves the reservation by its ID, checks if it is already confirmed, and if not, updates its status to CONFIRMED. Finally, it maps the updated reservation to a response DTO and returns it.
    @Override
    public ReservationResponse confirmReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation introuvable"));
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return mapToResponse(reservation);
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return mapToResponse(reservation);
    }

    // This method is used to cancel a reservation. It retrieves the reservation by its ID, updates its status to CANCELLED, and adds any cancellation reason as notes. Finally, it maps the updated reservation to a response DTO and returns it.
    @Override
    public ReservationResponse cancelReservation(Long reservationId, String reason) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation introuvable"));
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setNotes(reason);
        return mapToResponse(reservation);
    }

  // delete reservation
    @Override
    public void deleteReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation introuvable"));
        reservationRepository.delete(reservation);
    }

    // This method is used to list all reservations. It retrieves all reservations from the repository, maps each reservation to a response DTO, and returns the list of response DTOs.
    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> listReservations() {
        return reservationRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    // This method is used to get a specific reservation by its ID. It retrieves the reservation from the repository, maps it to a response DTO, and returns it. If the reservation is not found, it throws an IllegalArgumentException.
    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new IllegalArgumentException("Reservation introuvable"));
    }

    //
    private void validateDates(ReservationRequest request) {
        if (request.pickupDate() == null || request.returnDate() == null) {
            throw new IllegalArgumentException("Les dates sont obligatoires");
        }
        if (!request.returnDate().isAfter(request.pickupDate())) {
            throw new IllegalArgumentException("La date de retour doit être postérieure");
        }
    }

    // This method checks if there are any overlapping reservations for the same car and date range. It queries the repository for any reservations that overlap with the requested pickup and return dates, and if it finds any, it throws an IllegalStateException indicating that the vehicle is already reserved for that period.
    private void ensureAvailability(ReservationRequest request) {
        List<Reservation> overlaps = reservationRepository.findOverlapping(
                request.carId(),
                request.pickupDate(),
                request.returnDate(),
                List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED)
        );
        if (!overlaps.isEmpty()) {
            throw new IllegalStateException("Le véhicule est déjà réservé sur cette période");
        }
    }

    private Customer saveCustomer(ReservationRequest request) {
        Customer customer = Customer.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .documentId(request.documentId())
                .build();
        return customerRepository.save(customer);
    }

    private void attachExtras(Reservation reservation, List<ReservationExtraRequest> extras) {
        if (extras == null) {
            return;
        }
        extras.forEach(extraRequest -> {
            ReservationExtra extra = ReservationExtra.builder()
                    .reservation(reservation)
                    .label(extraRequest.label())
                    .quantity(extraRequest.quantity())
                    .unitPrice(extraRequest.unitPrice())
                    .build();
            reservation.getExtras().add(extra);
        });
    }

    private void computeTotals(Reservation reservation) {
        int days = daysBetween(reservation.getPickupDate(), reservation.getReturnDate());
        reservation.setDaysCount(days);

        BigDecimal extrasTotal = reservation.getExtras().stream()
                .map(extra -> extra.getUnitPrice().multiply(BigDecimal.valueOf(extra.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal base = reservation.getDailyRate().multiply(BigDecimal.valueOf(days));
        reservation.setTotalAmount(base.add(extrasTotal));
    }

    private int daysBetween(ReservationRequest request) {
        return daysBetween(request.pickupDate(), request.returnDate());
    }

    private int daysBetween(java.time.LocalDate start, java.time.LocalDate end) {
        return (int) ChronoUnit.DAYS.between(start, end);
    }

    private ReservationResponse mapToResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getCarId(),
                reservation.getStatus(),
                reservation.getCustomer().getFirstName(),
                reservation.getCustomer().getLastName(),
                reservation.getCustomer().getEmail(),
                reservation.getCustomer().getPhone(),
                reservation.getPickupCity(),
                reservation.getPickupDate(),
                reservation.getReturnCity(),
                reservation.getReturnDate(),
                reservation.getTotalAmount(),
                reservation.getExtras().stream().map(ReservationExtra::getLabel).toList(),
                reservation.getNotes()
        );
    }
}

