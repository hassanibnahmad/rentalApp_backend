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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Hourly scan: looks for CONFIRMED reservations whose pickup is within the
 * next 24 hours and that haven't been reminded yet. Emits a
 * RENTAL_UPCOMING event so the rental_reminder flow runs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RentalReminderCron {

    private final AutomationProperties properties;
    private final ReservationRepository reservationRepository;
    private final AutomationEngine engine;

    @Scheduled(fixedDelayString = "${automation.timing.reminder-cron-interval-ms:3600000}",
               initialDelay = 30_000)
    @Transactional(readOnly = true)
    public void tick() {
        if (!properties.isEnabled()) return;
        int leadHours = properties.getTiming().getReminderLeadHours();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = now.plusNanos((long) leadHours * 3_600_000_000_000L - 30L * 60_000_000_000L);
        OffsetDateTime to   = now.plusNanos((long) (leadHours + 2) * 3_600_000_000_000L);

        List<Reservation> due = reservationRepository.findConfirmedPickupBetween(
                ReservationStatus.CONFIRMED, from.toLocalDate(), to.toLocalDate());
        log.info("rental_reminder_candidates count={}", due.size());
        for (Reservation r : due) {
            if (Boolean.TRUE.equals(r.getReminderSent())) continue;
            engine.dispatch(
                    AutomationEventType.RENTAL_UPCOMING,
                    String.valueOf(r.getId()),
                    "cron",
                    Map.of(
                            "reservationId", r.getId(),
                            "source", "rental_reminder_cron",
                            "leadHours", leadHours
                    )
            );
        }
    }
}
