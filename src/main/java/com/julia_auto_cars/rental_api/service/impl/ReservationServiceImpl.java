package com.julia_auto_cars.rental_api.service.impl;

import com.julia_auto_cars.rental_api.automation.model.AutomationEventType;
import com.julia_auto_cars.rental_api.automation.model.JobStatus;
import com.julia_auto_cars.rental_api.automation.repository.AutomationEventRepository;
import com.julia_auto_cars.rental_api.automation.repository.AutomationJobRepository;
import com.julia_auto_cars.rental_api.automation.service.AutomationEngine;
import com.julia_auto_cars.rental_api.dto.ReservationExtraRequest;
import com.julia_auto_cars.rental_api.dto.ReservationRequest;
import com.julia_auto_cars.rental_api.dto.ReservationResponse;
import com.julia_auto_cars.rental_api.model.*;
import com.julia_auto_cars.rental_api.repository.CarRepository;
import com.julia_auto_cars.rental_api.repository.CustomerRepository;
import com.julia_auto_cars.rental_api.repository.ReservationRepository;
import com.julia_auto_cars.rental_api.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final CustomerRepository customerRepository;
    private final CarRepository carRepository;
    private final AutomationEngine automationEngine;
    private final AutomationEventRepository automationEventRepository;
    private final AutomationJobRepository automationJobRepository;

    // This method is used to create a new reservation. It validates the input data, checks for availability, saves the customer if necessary, creates the reservation, attaches any extras, computes the totals, and saves the reservation to the repository. Finally, it maps the saved reservation to a response DTO and returns it.
    @Override
    public ReservationResponse createReservation(ReservationRequest request) {
        validateDates(request);
        ensureAvailability(request);
        Car car = carRepository.findById(request.carId())
                .orElseThrow(() -> new IllegalArgumentException("Véhicule introuvable"));

        Integer pricePerDay = car.getPricePerDay();
        if (pricePerDay == null || pricePerDay <= 0) {
            throw new IllegalStateException("Le tarif journalier du véhicule est invalide");
        }

        Customer customer = upsertCustomer(request);

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

        // Emit a BOOKING_STARTED event so the abandoned flow can be scheduled.
        try {
            var payload = new HashMap<String, Object>();
            payload.put("reservationId", saved.getId());
            payload.put("carId", saved.getCarId());
            payload.put("customerId", saved.getCustomer() != null ? saved.getCustomer().getId() : null);
            payload.put("status", saved.getStatus().name());
            automationEngine.dispatch(
                    AutomationEventType.BOOKING_STARTED,
                    String.valueOf(saved.getId()),
                    "service",
                    payload
            );
        } catch (Exception e) {
            log.warn("automation_dispatch_failed_on_create reservationId={} err={}", saved.getId(), e.getMessage());
        }

        return mapToResponse(saved);
    }

    @Override
    public ReservationResponse updateReservation(Long reservationId, ReservationRequest request) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation introuvable"));

        validateDates(request);
        ensureAvailabilityForUpdate(reservationId, request);

        Car car = carRepository.findById(request.carId())
                .orElseThrow(() -> new IllegalArgumentException("Véhicule introuvable"));

        Integer pricePerDay = car.getPricePerDay();
        if (pricePerDay == null || pricePerDay <= 0) {
            throw new IllegalStateException("Le tarif journalier du véhicule est invalide");
        }

        Customer customer = upsertCustomer(request);
        reservation.getExtras().clear();

        reservation.setCarId(request.carId());
        reservation.setCustomer(customer);
        reservation.setPickupCity(request.pickupCity());
        reservation.setPickupDate(request.pickupDate());
        reservation.setReturnCity(request.returnCity());
        reservation.setReturnDate(request.returnDate());
        reservation.setDailyRate(BigDecimal.valueOf(pricePerDay));
        reservation.setNotes(request.notes());

        attachExtras(reservation, request.extras());
        computeTotals(reservation);
        return mapToResponse(reservationRepository.save(reservation));
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
        Reservation saved = reservationRepository.save(reservation);

        // Cancel any pending abandon jobs since the reservation is now confirmed.
        cancelAbandonJobs(saved.getId());

        // Trigger both the booking_confirmation flow and the upsell flow.
        try {
            var payload = new HashMap<String, Object>();
            payload.put("reservationId", saved.getId());
            payload.put("carId", saved.getCarId());
            payload.put("customerId", saved.getCustomer() != null ? saved.getCustomer().getId() : null);
            payload.put("status", saved.getStatus().name());
            automationEngine.dispatch(
                    AutomationEventType.BOOKING_CONFIRMED,
                    String.valueOf(saved.getId()),
                    "service",
                    payload
            );
        } catch (Exception e) {
            log.warn("automation_dispatch_failed_on_confirm reservationId={} err={}", saved.getId(), e.getMessage());
        }
        return mapToResponse(saved);
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

    /**
     * Mark a reservation as completed (after the rental period). Triggers the
     * review-request flow 24h later via the RENTAL_COMPLETED event.
     */
    public ReservationResponse completeReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation introuvable"));
        reservation.setStatus(ReservationStatus.COMPLETED);
        Reservation saved = reservationRepository.save(reservation);
        try {
            automationEngine.dispatch(
                    AutomationEventType.RENTAL_COMPLETED,
                    String.valueOf(saved.getId()),
                    "service",
                    Map.of(
                            "reservationId", saved.getId(),
                            "carId", saved.getCarId(),
                            "customerId", saved.getCustomer() != null ? saved.getCustomer().getId() : null,
                            "status", saved.getStatus().name()
                    )
            );
        } catch (Exception e) {
            log.warn("automation_dispatch_failed_on_complete reservationId={} err={}", saved.getId(), e.getMessage());
        }
        return mapToResponse(saved);
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
        int days = daysBetween(request);
        if (!request.returnDate().isAfter(request.pickupDate())) {
            throw new IllegalArgumentException("La date de retour doit être postérieure");
        }
        if (days < 2) {
            throw new IllegalArgumentException("La réservation doit durer au minimum 2 jours");
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

    private void ensureAvailabilityForUpdate(Long reservationId, ReservationRequest request) {
        List<Reservation> overlaps = reservationRepository.findOverlappingExcludingId(
                reservationId,
                request.carId(),
                request.pickupDate(),
                request.returnDate(),
                List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED)
        );
        if (!overlaps.isEmpty()) {
            throw new IllegalStateException("Le véhicule est déjà réservé sur cette période");
        }
    }

    private Customer upsertCustomer(ReservationRequest request) {
        Customer customer = customerRepository
                .findByEmail(request.email())
                .orElseGet(Customer::new);
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
        customer.setDocumentId(request.documentId());
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

    private void cancelAbandonJobs(Long reservationId) {
        automationEventRepository.findByExternalIdAndType(
                String.valueOf(reservationId), AutomationEventType.BOOKING_STARTED)
            .ifPresent(event -> {
                automationJobRepository.findFirstByEventIdAndFlow(event.getId(), "booking_abandoned")
                    .ifPresent(job -> {
                        if (job.getStatus() == JobStatus.SCHEDULED || job.getStatus() == JobStatus.RUNNING) {
                            job.setStatus(JobStatus.CANCELLED);
                            job.setFinishedAt(OffsetDateTime.now());
                            automationJobRepository.save(job);
                            log.info("cancelled_abandon_job reservationId={} jobId={}", reservationId, job.getId());
                        }
                    });
            });
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
                reservation.getCustomer().getDocumentId(),
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

