package com.julia_auto_cars.rental_api.automation.flow;

import com.julia_auto_cars.rental_api.automation.model.Agency;
import com.julia_auto_cars.rental_api.model.Car;
import com.julia_auto_cars.rental_api.model.Customer;
import com.julia_auto_cars.rental_api.model.Reservation;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Snapshot of all data a flow needs to evaluate conditions and render templates.
 *
 * <p>Two views are exposed per the spec:</p>
 * <ul>
 *   <li>{@code booking}: lifecycle view (status, abandoned_sent, …)</li>
 *   <li>{@code rental}: operational view (completed, reminder_sent, …)</li>
 * </ul>
 *
 * <p>Both are derived from the underlying {@link Reservation} row.</p>
 */
public record FlowContext(
        Reservation reservation,
        BookingView booking,
        RentalView rental,
        Customer customer,
        Car car,
        Agency agency,
        java.util.Map<String, Object> metadata,
        String bookingLink,
        OffsetDateTime now
) {

    public record BookingView(
            String id,
            String status,           // 'pending' | 'confirmed' | 'cancelled' | 'completed' | 'draft'
            boolean abandoned_sent,
            boolean confirmation_sent,
            java.time.LocalDate start_date,
            java.time.LocalDate end_date,
            String user_id,
            String car_id,
            String link
    ) {}

    public record RentalView(
            String id,
            boolean completed,
            boolean reminder_sent,
            boolean review_sent,
            java.time.LocalDate start_date,
            java.time.LocalDate end_date,
            String startTime
    ) {}

    public static FlowContext empty() {
        return new FlowContext(null, null, null, null, null, null,
                java.util.Map.of(), null, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
