package com.julia_auto_cars.rental_api.automation.service;

import com.julia_auto_cars.rental_api.automation.action.FlowAction;
import com.julia_auto_cars.rental_api.automation.condition.ConditionEvaluator;
import com.julia_auto_cars.rental_api.automation.flow.Durations;
import com.julia_auto_cars.rental_api.automation.flow.FlowContext;
import com.julia_auto_cars.rental_api.automation.flow.FlowContextBuilder;
import com.julia_auto_cars.rental_api.automation.flow.FlowDefinition;
import com.julia_auto_cars.rental_api.automation.flow.FlowRegistry;
import com.julia_auto_cars.rental_api.automation.model.AutomationEvent;
import com.julia_auto_cars.rental_api.automation.model.AutomationJob;
import com.julia_auto_cars.rental_api.automation.model.JobStatus;
import com.julia_auto_cars.rental_api.automation.model.MessageStatus;
import com.julia_auto_cars.rental_api.automation.model.WhatsAppMessage;
import com.julia_auto_cars.rental_api.automation.repository.AutomationEventRepository;
import com.julia_auto_cars.rental_api.automation.repository.AutomationJobRepository;
import com.julia_auto_cars.rental_api.automation.repository.WhatsAppMessageRepository;
import com.julia_auto_cars.rental_api.automation.template.MessageRenderer;
import com.julia_auto_cars.rental_api.automation.template.TemplateRegistry;
import com.julia_auto_cars.rental_api.automation.whatsapp.WhatsAppClient;
import com.julia_auto_cars.rental_api.model.Reservation;
import com.julia_auto_cars.rental_api.repository.CarRepository;
import com.julia_auto_cars.rental_api.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Executes a single {@link AutomationJob}. Walks the flow's action list
 * sequentially, handling each action type:
 * <ul>
 *   <li>{@code delay}: pause the job by rescheduling it for a future runAt.</li>
 *   <li>{@code condition}: evaluate; abort the job if false.</li>
 *   <li>{@code send_whatsapp}: render template and call the WhatsApp client.</li>
 *   <li>{@code update_flag}: set the named flag on the reservation.</li>
 *   <li>{@code cron_check}: no-op at execution time (handled by the scheduler).</li>
 * </ul>
 *
 * <p>For long delays (>= 1 minute) we reschedule rather than block the
 * worker thread. This makes the system fully restart-safe: the job is
 * persisted in the database, and the {@link com.julia_auto_cars.rental_api.automation.scheduler.JobDispatcher}
 * picks it up at the right time.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationWorker {

    private static final long INLINE_DELAY_THRESHOLD_MS = 60_000L;

    private final FlowRegistry flowRegistry;
    private final FlowContextBuilder contextBuilder;
    private final AutomationJobRepository jobRepository;
    private final AutomationEventRepository eventRepository;
    private final WhatsAppMessageRepository messageRepository;
    private final WhatsAppClient whatsAppClient;
    private final ReservationRepository reservationRepository;
    private final CarRepository carRepository;

    /**
     * Process one job. Called from the {@code JobDispatcher} on a worker thread.
     * Returns {@code true} if the job moved to a terminal state (completed/failed/cancelled).
     */
    @Transactional
    public boolean run(UUID jobId) {
        Optional<AutomationJob> opt = jobRepository.findById(jobId);
        if (opt.isEmpty()) {
            log.warn("run_job_not_found id={}", jobId);
            return true;
        }
        AutomationJob job = opt.get();
        if (job.getStatus() != JobStatus.SCHEDULED && job.getStatus() != JobStatus.RUNNING) {
            log.info("run_job_skipping status={} id={}", job.getStatus(), jobId);
            return true;
        }

        // Mark as running (if first attempt)
        if (job.getStatus() == JobStatus.SCHEDULED) {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(OffsetDateTime.now());
            job.setAttempts(job.getAttempts() + 1);
            jobRepository.save(job);
        }

        FlowDefinition flow = flowRegistry.findByName(job.getFlow());
        if (flow == null) {
            fail(job, "Unknown flow: " + job.getFlow());
            return true;
        }

        // Resolve context from event payload
        Optional<AutomationEvent> eventOpt = eventRepository.findById(job.getEventId());
        Map<String, Object> eventPayload = eventOpt.map(AutomationEvent::getPayload).orElse(Map.of());
        Long reservationId = parseReservationId(eventPayload, job);
        if (reservationId == null) {
            fail(job, "Cannot resolve reservation id from event payload");
            return true;
        }
        Optional<Reservation> resOpt = reservationRepository.findById(reservationId);
        if (resOpt.isEmpty()) {
            fail(job, "Reservation not found: " + reservationId);
            return true;
        }
        Reservation reservation = resOpt.get();
        var car = reservation.getCarId() != null
                ? carRepository.findById(reservation.getCarId()).orElse(null)
                : null;
        FlowContext ctx = contextBuilder.build(reservation, reservation.getCustomer(), car, eventPayload);

        try {
            walk(job, flow, ctx);
        } catch (Exception ex) {
            log.error("run_job_exception id={}", jobId, ex);
            fail(job, ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            return true;
        }
        return job.getStatus() == JobStatus.COMPLETED
                || job.getStatus() == JobStatus.FAILED
                || job.getStatus() == JobStatus.CANCELLED;
    }

    private void walk(AutomationJob job, FlowDefinition flow, FlowContext ctx) {
        for (int i = job.getActionIndex(); i < flow.actions().size(); i++) {
            FlowAction action = flow.actions().get(i);
            log.info("flow_action_start flow={} jobId={} action={} index={}",
                    flow.name(), job.getId(), action.type(), i);

            if (action instanceof FlowAction.Delay d) {
                long delayMs = Durations.toMillis(d.duration());
                job.setAccumulatedDelayMs(job.getAccumulatedDelayMs() + delayMs);

                if (delayMs <= INLINE_DELAY_THRESHOLD_MS) {
                    // Short delay: block this thread briefly.
                    sleep(delayMs);
                } else {
                    // Long delay: reschedule the job to resume from the NEXT action.
                    job.setActionIndex(i + 1);
                    job.setRunAt(OffsetDateTime.now().plusNanos(delayMs * 1_000_000));
                    job.setStatus(JobStatus.SCHEDULED);
                    jobRepository.save(job);
                    log.info("flow_delay_rescheduled jobId={} resumeAt={} remainingActions={}",
                            job.getId(), job.getRunAt(), flow.actions().size() - (i + 1));
                    return;
                }
                continue;
            }

            if (action instanceof FlowAction.Condition c) {
                boolean ok = ConditionEvaluator.evaluate(c.expression(), ctx);
                log.info("flow_condition_evaluated result={} expression='{}'", ok, c.expression());
                if (!ok) {
                    log.info("flow_condition_false_skip_remaining jobId={}", job.getId());
                    job.setStatus(JobStatus.COMPLETED);
                    job.setFinishedAt(OffsetDateTime.now());
                    jobRepository.save(job);
                    return;
                }
                continue;
            }

            if (action instanceof FlowAction.SendWhatsApp s) {
                handleSendWhatsApp(s, ctx, job);
                continue;
            }

            if (action instanceof FlowAction.UpdateFlag u) {
                handleUpdateFlag(u, ctx, job);
                continue;
            }

            if (action instanceof FlowAction.CronCheck) {
                // No-op at execution time.
                continue;
            }
        }

        job.setStatus(JobStatus.COMPLETED);
        job.setFinishedAt(OffsetDateTime.now());
        jobRepository.save(job);
        log.info("flow_completed flow={} jobId={}", flow.name(), job.getId());
    }

    private void handleSendWhatsApp(FlowAction.SendWhatsApp s, FlowContext ctx, AutomationJob job) {
        if (ctx.customer() == null || ctx.customer().getPhone() == null || ctx.customer().getPhone().isBlank()) {
            fail(job, "No phone number for customer");
            return;
        }
        TemplateRegistry.Template template = TemplateRegistry.get(s.templateId());
        if (template == null) {
            fail(job, "Unknown template: " + s.templateId());
            return;
        }
        String body = MessageRenderer.render(template.body(), ctx, s.variables());
        WhatsAppClient.SendResult result = whatsAppClient.sendText(ctx.customer().getPhone(), body);

        // Always log the message — successes and failures.
        WhatsAppMessage msg = WhatsAppMessage.builder()
                .jobId(job.getId())
                .templateId(template.id())
                .body(body)
                .toPhone(ctx.customer().getPhone())
                .providerMessageId(result.providerMessageId())
                .status(result.ok() ? MessageStatus.SENT : MessageStatus.FAILED)
                .providerError(result.error())
                .sentAt(OffsetDateTime.now())
                .build();
        messageRepository.save(msg);

        if (!result.ok()) {
            log.warn("whatsapp_send_failed jobId={} template={} error={}",
                    job.getId(), template.id(), result.error());
        } else {
            log.info("whatsapp_sent jobId={} template={} providerId={}",
                    job.getId(), template.id(), result.providerMessageId());
        }
    }

    private void handleUpdateFlag(FlowAction.UpdateFlag u, FlowContext ctx, AutomationJob job) {
        if (ctx.reservation() == null) {
            fail(job, "No reservation to update");
            return;
        }
        // Map spec flag names → Reservation entity fields.
        switch (u.field()) {
            case "abandoned_sent"    -> ctx.reservation().setAbandonedSent(toBool(u.value()));
            case "confirmation_sent" -> ctx.reservation().setConfirmationSent(toBool(u.value()));
            case "reminder_sent"     -> ctx.reservation().setReminderSent(toBool(u.value()));
            case "review_sent"       -> ctx.reservation().setReviewSent(toBool(u.value()));
            case "upsell_sent"       -> ctx.reservation().setUpsellSent(toBool(u.value()));
            default -> {
                log.warn("flag_not_recognised field={}", u.field());
                return;
            }
        }
        reservationRepository.save(ctx.reservation());
    }

    private void fail(AutomationJob job, String reason) {
        job.setStatus(JobStatus.FAILED);
        job.setFinishedAt(OffsetDateTime.now());
        job.setError(reason);
        jobRepository.save(job);
        log.warn("job_failed id={} reason={}", job.getId(), reason);
    }

    private static Long parseReservationId(Map<String, Object> payload, AutomationJob job) {
        if (payload == null) return null;
        Object v = payload.get("reservationId");
        if (v == null) v = payload.get("reservation_id");
        if (v == null) return null;
        try {
            if (v instanceof Number n) return n.longValue();
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        if (v instanceof Number n) return n.intValue() != 0;
        return false;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
