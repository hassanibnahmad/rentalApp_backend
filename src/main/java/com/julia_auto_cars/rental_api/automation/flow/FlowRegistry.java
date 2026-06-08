package com.julia_auto_cars.rental_api.automation.flow;

import com.julia_auto_cars.rental_api.automation.action.FlowAction;

import java.util.List;

import static com.julia_auto_cars.rental_api.automation.action.FlowAction.*;

/**
 * Central registry of every flow the engine knows about.
 * Order matters: each event may map to multiple flows, all are triggered.
 *
 * <p>Durations are filled in from {@link com.julia_auto_cars.rental_api.automation.config.AutomationProperties}
 * at construction time so they can be tuned without code changes.</p>
 */
public final class FlowRegistry {

    private final int abandonTimeoutMinutes;
    private final int upsellDelayMinutes;
    private final int reviewDelayHours;
    private final long reminderCronIntervalMs;

    private final List<FlowDefinition> flows;

    public FlowRegistry(int abandonTimeoutMinutes,
                        int upsellDelayMinutes,
                        int reviewDelayHours,
                        long reminderCronIntervalMs) {
        this.abandonTimeoutMinutes = abandonTimeoutMinutes;
        this.upsellDelayMinutes = upsellDelayMinutes;
        this.reviewDelayHours = reviewDelayHours;
        this.reminderCronIntervalMs = reminderCronIntervalMs;
        this.flows = build();
    }

    public List<FlowDefinition> all() { return flows; }

    public List<FlowDefinition> findByEvent(String eventType) {
        return flows.stream().filter(f -> f.triggerEvent().equals(eventType)).toList();
    }

    public FlowDefinition findByName(String name) {
        return flows.stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
    }

    private List<FlowDefinition> build() {
        return List.of(
            new FlowDefinition(
                "booking_abandoned",
                "BOOKING_STARTED",
                "Relaunch abandoned bookings 10m after the customer starts the flow.",
                List.of(
                    new Delay(abandonTimeoutMinutes + "m"),
                    new Condition("booking.status === 'pending' && !booking.abandoned_sent"),
                    new SendWhatsApp("booking_abandoned",
                            List.of("user.name", "booking.link")),
                    new UpdateFlag("abandoned_sent", true)
                )
            ),
            new FlowDefinition(
                "booking_confirmation",
                "BOOKING_CONFIRMED",
                "Send a confirmation WhatsApp as soon as the reservation is confirmed.",
                List.of(
                    new Condition("!booking.confirmation_sent"),
                    new SendWhatsApp("booking_confirmation",
                            List.of("user.name", "car.name", "booking.start_date", "booking.end_date",
                                   "agency.location", "agency.phone")),
                    new UpdateFlag("confirmation_sent", true)
                )
            ),
            new FlowDefinition(
                "rental_reminder",
                "RENTAL_UPCOMING",
                "Cron-driven flow that sends a reminder 24h before the rental starts.",
                List.of(
                    new CronCheck(reminderCronIntervalMs + "ms"),
                    new Condition("differenceInHours(rental.start_date, now) <= 24 && !rental.reminder_sent"),
                    new SendWhatsApp("rental_reminder",
                            List.of("user.name", "agency.location", "rental.start_time")),
                    new UpdateFlag("reminder_sent", true)
                )
            ),
            new FlowDefinition(
                "upsell",
                "BOOKING_CONFIRMED",
                "Send an upsell message 5m after confirmation.",
                List.of(
                    new Delay(upsellDelayMinutes + "m"),
                    new SendWhatsApp("upsell_options", List.of())
                )
            ),
            new FlowDefinition(
                "review_request",
                "RENTAL_COMPLETED",
                "24h after the rental ends, ask the customer for a review.",
                List.of(
                    new Delay(reviewDelayHours + "h"),
                    new Condition("rental.completed === true && !rental.review_sent"),
                    new SendWhatsApp("review_request",
                            List.of("user.name", "agency.review_link")),
                    new UpdateFlag("review_sent", true)
                )
            )
        );
    }
}
