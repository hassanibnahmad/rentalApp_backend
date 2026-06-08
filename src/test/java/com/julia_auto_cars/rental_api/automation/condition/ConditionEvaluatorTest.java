package com.julia_auto_cars.rental_api.automation.condition;

import com.julia_auto_cars.rental_api.automation.flow.FlowContext;
import com.julia_auto_cars.rental_api.automation.flow.FlowContextBuilder;
import com.julia_auto_cars.rental_api.automation.model.Agency;
import com.julia_auto_cars.rental_api.model.Reservation;
import com.julia_auto_cars.rental_api.model.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {

    @Test
    void parsesBookingStatusCondition() {
        FlowContext ctx = ctxWithStatus(ReservationStatus.PENDING_PAYMENT, false, false);
        assertTrue(ConditionEvaluator.evaluate("booking.status === 'pending'", ctx));
        assertFalse(ConditionEvaluator.evaluate("booking.status === 'confirmed'", ctx));
    }

    @Test
    void parsesAbandonedFlowCondition() {
        FlowContext ctx = ctxWithStatus(ReservationStatus.PENDING_PAYMENT, false, false);
        assertTrue(ConditionEvaluator.evaluate(
                "booking.status === 'pending' && !booking.abandoned_sent", ctx));
    }

    @Test
    void parsesConfirmationCondition() {
        FlowContext ctx = ctxWithStatus(ReservationStatus.CONFIRMED, true, false);
        assertFalse(ConditionEvaluator.evaluate("!booking.confirmation_sent", ctx));

        FlowContext fresh = ctxWithStatus(ReservationStatus.CONFIRMED, false, false);
        assertTrue(ConditionEvaluator.evaluate("!booking.confirmation_sent", fresh));
    }

    @Test
    void parsesReminderCondition() {
        FlowContext ctx = ctxWithStatusAndPickup(
                ReservationStatus.CONFIRMED, false, false, LocalDate.now().plusDays(1));
        assertTrue(ConditionEvaluator.evaluate(
                "differenceInHours(rental.start_date, now) <= 24 && !rental.reminder_sent", ctx));
    }

    @Test
    void parsesReviewCondition() {
        FlowContext ctx = ctxWithStatus(ReservationStatus.COMPLETED, false, false);
        assertTrue(ConditionEvaluator.evaluate(
                "rental.completed === true && !rental.review_sent", ctx));
    }

    // ── helpers ─────────────────────────────────────────────────────────
    private FlowContext ctxWithStatus(ReservationStatus status, boolean confirmationSent, boolean reminderSent) {
        return ctxWithStatusAndPickup(status, confirmationSent, reminderSent, LocalDate.now().plusDays(5));
    }

    private FlowContext ctxWithStatusAndPickup(ReservationStatus status,
                                               boolean confirmationSent,
                                               boolean reminderSent,
                                               LocalDate pickup) {
        Reservation r = new Reservation();
        r.setId(42L);
        r.setCarId(1L);
        r.setStatus(status);
        r.setPickupDate(pickup);
        r.setReturnDate(pickup.plusDays(3));
        setFlag(r, "confirmationSent", confirmationSent);
        setFlag(r, "reminderSent", reminderSent);

        Agency agency = Agency.builder()
                .name("Julia Auto Cars")
                .location("Casablanca – Aéroport Mohammed V")
                .phone("+212600000000")
                .build();
        return new FlowContext(
                r,
                new FlowContext.BookingView(
                        "42", "pending", false, confirmationSent,
                        pickup, pickup.plusDays(3), "1", "1", null),
                new FlowContext.RentalView(
                        "42", status == ReservationStatus.COMPLETED, reminderSent, false,
                        pickup, pickup.plusDays(3), null),
                null, null, agency,
                Map.of(),
                null,
                java.time.OffsetDateTime.now()
        );
    }

    private static void setFlag(Reservation r, String fieldName, boolean value) {
        try {
            Field f = Reservation.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(r, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
