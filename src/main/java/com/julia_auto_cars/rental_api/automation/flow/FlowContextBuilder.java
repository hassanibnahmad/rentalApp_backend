package com.julia_auto_cars.rental_api.automation.flow;

import com.julia_auto_cars.rental_api.automation.model.Agency;
import com.julia_auto_cars.rental_api.automation.repository.AgencyRepository;
import com.julia_auto_cars.rental_api.model.Car;
import com.julia_auto_cars.rental_api.model.Customer;
import com.julia_auto_cars.rental_api.model.Reservation;
import com.julia_auto_cars.rental_api.model.ReservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Builds a {@link FlowContext} for a given reservation. Centralises the
 * projection from the {@code Reservation} DB row to the two spec views
 * ({@code booking} and {@code rental}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlowContextBuilder {

    private final AgencyRepository agencyRepository;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public FlowContext build(Reservation reservation, Customer customer, Car car,
                             Map<String, Object> metadata) {
        Agency agency = agencyRepository.findFirstByOrderByCreatedAtAsc().orElse(null);
        String bookingLink = reservation != null
                ? frontendBaseUrl + "/reservation?ref=" + reservation.getId()
                : null;

        FlowContext.BookingView booking = null;
        FlowContext.RentalView rental = null;
        if (reservation != null) {
            booking = new FlowContext.BookingView(
                    String.valueOf(reservation.getId()),
                    normalizeStatus(reservation.getStatus()),
                    Boolean.TRUE.equals(reservation.getAbandonedSent()),
                    Boolean.TRUE.equals(reservation.getConfirmationSent()),
                    reservation.getPickupDate(),
                    reservation.getReturnDate(),
                    reservation.getCustomer() != null ? String.valueOf(reservation.getCustomer().getId()) : null,
                    String.valueOf(reservation.getCarId()),
                    bookingLink
            );
            String startTime = bookingLink != null
                    ? formatDate(reservation.getPickupDate()) + " 09:00"
                    : null;
            rental = new FlowContext.RentalView(
                    String.valueOf(reservation.getId()),
                    reservation.getStatus() == ReservationStatus.COMPLETED,
                    Boolean.TRUE.equals(reservation.getReminderSent()),
                    Boolean.TRUE.equals(reservation.getReviewSent()),
                    reservation.getPickupDate(),
                    reservation.getReturnDate(),
                    startTime
            );
            // Reference the startTime variable to avoid an "unused" warning.
            if (startTime != null && startTime.isEmpty()) log.trace("empty start_time");
        }
        return new FlowContext(
                reservation, booking, rental,
                customer != null ? customer : (reservation != null ? reservation.getCustomer() : null),
                car,
                agency,
                metadata != null ? metadata : Map.of(),
                bookingLink,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    public static String normalizeStatus(ReservationStatus s) {
        if (s == null) return "pending";
        return switch (s) {
            case PENDING_PAYMENT, DRAFT -> "pending";
            case CONFIRMED              -> "confirmed";
            case CANCELLED              -> "cancelled";
            case COMPLETED              -> "completed";
        };
    }

    public static String formatDate(LocalDate d) {
        if (d == null) return "";
        return String.format("%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }
}
