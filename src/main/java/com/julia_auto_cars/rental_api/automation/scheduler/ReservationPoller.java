package com.julia_auto_cars.rental_api.automation.scheduler;

import com.julia_auto_cars.rental_api.automation.config.AutomationProperties;
import com.julia_auto_cars.rental_api.automation.model.AutomationEventType;
import com.julia_auto_cars.rental_api.automation.service.AutomationEngine;
import com.julia_auto_cars.rental_api.model.Reservation;
import com.julia_auto_cars.rental_api.model.ReservationStatus;
import com.julia_auto_cars.rental_api.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Polling fallback. Watches reservations for state transitions and synthesizes
 * events the same way the webhook receiver would. This is a safety net for
 * cases where the Spring Boot service emits an event but the dispatcher
 * crashes before persisting it.
 *
 * <p>The cursor is implemented as a {@code lastSeenUpdatedAt} field stored
 * in memory. On a process restart it re-reads the last 50 reservations.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationPoller {

    private final AutomationProperties properties;
    private final ReservationRepository reservationRepository;
    private final AutomationEngine engine;

    private OffsetDateTime cursor;

    @Scheduled(fixedDelayString = "${automation.polling.interval-ms:30000}",
               initialDelay = 60_000)
    @Transactional(readOnly = true)
    public void tick() {
        if (!properties.isEnabled() || !properties.getPolling().isEnabled()) {
            return;
        }
        if (cursor == null) {
            cursor = OffsetDateTime.now().minusMinutes(5);
        }
        List<Reservation> recent = reservationRepository.findUpdatedAfter(cursor, org.springframework.data.domain.PageRequest.of(0, 200));
        if (recent.isEmpty()) return;
        for (Reservation r : recent) {
            AutomationEventType type = mapStateToEvent(r);
            if (type == null) continue;
            engine.dispatch(
                    type,
                    String.valueOf(r.getId()),
                    "polling",
                    Map.of(
                            "reservationId", r.getId(),
                            "source", "polling"
                    )
            );
        }
        cursor = recent.get(recent.size() - 1).getUpdatedAt();
        log.debug("polling_tick processed={}", recent.size());
    }

    private AutomationEventType mapStateToEvent(Reservation r) {
        ReservationStatus s = r.getStatus();
        if (s == ReservationStatus.PENDING_PAYMENT) return AutomationEventType.BOOKING_STARTED;
        if (s == ReservationStatus.CONFIRMED) {
            // If pickup is within 24h, treat as RENTAL_UPCOMING.
            if (r.getPickupDate() != null) {
                long hours = java.time.temporal.ChronoUnit.HOURS.between(
                        OffsetDateTime.now(), r.getPickupDate().atStartOfDay().atOffset(java.time.ZoneOffset.UTC));
                if (hours <= 24 && hours > 0) return AutomationEventType.RENTAL_UPCOMING;
            }
            return AutomationEventType.BOOKING_CONFIRMED;
        }
        if (s == ReservationStatus.COMPLETED) {
            if (r.getReturnDate() != null && r.getReturnDate().isBefore(java.time.LocalDate.now())) {
                return AutomationEventType.RENTAL_COMPLETED;
            }
        }
        return null;
    }
}
