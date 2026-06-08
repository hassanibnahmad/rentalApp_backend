package com.julia_auto_cars.rental_api.automation.service;

import com.julia_auto_cars.rental_api.automation.config.AutomationProperties;
import com.julia_auto_cars.rental_api.automation.flow.Durations;
import com.julia_auto_cars.rental_api.automation.flow.FlowDefinition;
import com.julia_auto_cars.rental_api.automation.flow.FlowRegistry;
import com.julia_auto_cars.rental_api.automation.model.AutomationEvent;
import com.julia_auto_cars.rental_api.automation.model.AutomationEventType;
import com.julia_auto_cars.rental_api.automation.model.AutomationJob;
import com.julia_auto_cars.rental_api.automation.model.JobStatus;
import com.julia_auto_cars.rental_api.automation.repository.AutomationEventRepository;
import com.julia_auto_cars.rental_api.automation.repository.AutomationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Central automation engine. The single entry point for every event in the
 * system. Responsibilities:
 * <ol>
 *   <li>Deduplicate incoming events (by source + externalId + type).</li>
 *   <li>Map events to flows via the {@link FlowRegistry}.</li>
 *   <li>For each matching flow, compute the initial delay (the first
 *       {@code delay} action's duration, if any), insert an
 *       {@link AutomationJob} row in the SCHEDULED state, and let the
 *       {@link com.julia_auto_cars.rental_api.automation.scheduler.JobDispatcher}
 *       pick it up when it's due.</li>
 *   <li>Mark the event as processed once all jobs are scheduled.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationEngine {

    private final AutomationProperties properties;
    private final FlowRegistry flowRegistry;
    private final AutomationEventRepository eventRepository;
    private final AutomationJobRepository jobRepository;

    public IngestResult dispatch(AutomationEventType type, String externalId,
                                 String source, Map<String, Object> payload) {
        if (!properties.isEnabled()) {
            log.debug("automation_disabled_ignoring type={}", type);
            return new IngestResult(UUID.randomUUID(), List.of(), false);
        }
        return doDispatch(type, externalId, source, payload);
    }

    @Transactional
    protected IngestResult doDispatch(AutomationEventType type, String externalId,
                                      String source, Map<String, Object> payload) {
        // 1) Dedupe — check by externalId + type regardless of source
        Optional<AutomationEvent> existing = eventRepository
                .findByExternalIdAndType(externalId, type);
        if (existing.isPresent()) {
            log.info("event_duplicate type={} reservationId={} source={} existingSource={}",
                    type, externalId, source, existing.get().getSource());
            return new IngestResult(existing.get().getId(), List.of(), true);
        }

        // 2) Insert event
        AutomationEvent event = AutomationEvent.builder()
                .type(type)
                .source(source)
                .externalId(externalId)
                .payload(payload != null ? payload : Map.of())
                .receivedAt(OffsetDateTime.now())
                .build();
        event = eventRepository.save(event);

        // 3) Find matching flows
        List<FlowDefinition> flows = flowRegistry.findByEvent(type.name());
        if (flows.isEmpty()) {
            log.warn("no_flows_for_event type={}", type);
            markEventProcessed(event.getId(), 0, null);
            return new IngestResult(event.getId(), List.of(), false);
        }

        // 4) Schedule each flow
        List<String> triggered = new ArrayList<>();
        for (FlowDefinition flow : flows) {
            try {
                long initialDelayMs = computeInitialDelayMs(flow);
                OffsetDateTime runAt = OffsetDateTime.now().plusNanos(initialDelayMs * 1_000_000);

                AutomationJob job = AutomationJob.builder()
                        .eventId(event.getId())
                        .flow(flow.name())
                        .status(JobStatus.SCHEDULED)
                        .runAt(runAt)
                        .actionIndex(0)
                        .accumulatedDelayMs(0)
                        .variables(payload != null ? payload : Map.of())
                        .createdAt(OffsetDateTime.now())
                        .build();
                jobRepository.save(job);
                triggered.add(flow.name());
                log.info("flow_scheduled flow={} eventId={} runAt={} initialDelay={}ms",
                        flow.name(), event.getId(), runAt, initialDelayMs);
            } catch (Exception e) {
                log.error("flow_schedule_failed flow={}", flow.name(), e);
            }
        }

        markEventProcessed(event.getId(), triggered.size(), null);
        return new IngestResult(event.getId(), triggered, false);
    }

    private void markEventProcessed(UUID eventId, int flowCount, String error) {
        eventRepository.findById(eventId).ifPresent(e -> {
            e.setProcessed(true);
            e.setProcessedAt(OffsetDateTime.now());
            e.setFlowCount(flowCount);
            if (error != null) e.setError(error);
            eventRepository.save(e);
        });
    }

    private long computeInitialDelayMs(FlowDefinition flow) {
        // Look for the first delay action and use its duration.
        // If none, run immediately.
        for (var action : flow.actions()) {
            if (action instanceof com.julia_auto_cars.rental_api.automation.action.FlowAction.Delay d) {
                try {
                    return Durations.toMillis(d.duration());
                } catch (Exception ex) {
                    log.warn("invalid_delay duration={} flow={}", d.duration(), flow.name());
                    return 0L;
                }
            }
        }
        return 0L;
    }

    public record IngestResult(UUID eventId, List<String> flowsTriggered, boolean duplicate) {}
}
