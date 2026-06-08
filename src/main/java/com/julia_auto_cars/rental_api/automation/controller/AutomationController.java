package com.julia_auto_cars.rental_api.automation.controller;

import com.julia_auto_cars.rental_api.automation.dto.AutomationDtos;
import com.julia_auto_cars.rental_api.automation.dto.AutomationEventRequest;
import com.julia_auto_cars.rental_api.automation.dto.AutomationEventResponse;
import com.julia_auto_cars.rental_api.automation.flow.FlowRegistry;
import com.julia_auto_cars.rental_api.automation.model.AutomationEventType;
import com.julia_auto_cars.rental_api.automation.model.JobStatus;
import com.julia_auto_cars.rental_api.automation.model.MessageStatus;
import com.julia_auto_cars.rental_api.automation.repository.AutomationEventRepository;
import com.julia_auto_cars.rental_api.automation.repository.AutomationJobRepository;
import com.julia_auto_cars.rental_api.automation.repository.WhatsAppMessageRepository;
import com.julia_auto_cars.rental_api.automation.service.AutomationEngine;
import com.julia_auto_cars.rental_api.automation.template.TemplateRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST surface for the automation module.
 *
 * <p>Consumed by the React admin dashboard (AdminPortal.tsx) and by external
 * integrations. Read endpoints require ADMIN; ingestion endpoints are public
 * for the time being — they should be protected by an HMAC signature in
 * production (the
 * {@link com.julia_auto_cars.rental_api.automation.controller.AutomationController#verifySignature}
 * helper is provided as a starting point).</p>
 */
@RestController
@RequestMapping("/api/automation")
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationEngine engine;
    private final AutomationEventRepository eventRepository;
    private final AutomationJobRepository jobRepository;
    private final WhatsAppMessageRepository messageRepository;
    private final FlowRegistry flowRegistry;

    // ─────────────────────────────────────────────────────────────────────
    // Event ingestion
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping("/events")
    public ResponseEntity<AutomationEventResponse> ingestEvent(
            @RequestHeader(value = "x-automation-signature", required = false) String signature,
            @Valid @RequestBody AutomationEventRequest req) {
        verifySignature(signature, req);
        AutomationEventType type;
        try {
            type = AutomationEventType.valueOf(req.type());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
        String source = req.source() == null || req.source().isBlank() ? "webhook" : req.source();
        AutomationEngine.IngestResult result = engine.dispatch(
                type,
                req.reservationId(),
                source,
                req.metadata() != null
                        ? mergeMap(Map.of("reservationId", req.reservationId()), req.metadata())
                        : Map.of("reservationId", req.reservationId())
        );
        AutomationEventResponse body = new AutomationEventResponse(
                result.eventId(), result.flowsTriggered(), result.duplicate());
        return ResponseEntity.status(result.duplicate() ? 200 : 202).body(body);
    }

    @PostMapping("/events/{type}")
    public ResponseEntity<AutomationEventResponse> ingestTyped(
            @PathVariable String type,
            @RequestBody Map<String, Object> body) {
        AutomationEventRequest req = new AutomationEventRequest(
                type.toUpperCase(),
                String.valueOf(body.getOrDefault("reservationId", body.get("id"))),
                "webhook",
                body
        );
        return ingestEvent(null, req);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Monitoring endpoints (consumed by the admin dashboard)
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "events", eventRepository.count(),
                "jobs", jobRepository.count(),
                "messages", messageRepository.count()
        );
    }

    @GetMapping("/events")
    public List<AutomationDtos.EventDto> listEvents(
            @RequestParam(defaultValue = "50") int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        return eventRepository.findAll(PageRequest.of(0, capped,
                        Sort.by(Sort.Direction.DESC, "receivedAt")))
                .map(AutomationDtos.EventDto::from)
                .toList();
    }

    @GetMapping("/jobs")
    public List<AutomationDtos.JobDto> listJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String flow,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        int capped = Math.min(Math.max(limit, 1), 200);
        var pageable = PageRequest.of(offset / Math.max(capped, 1), capped,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var page = (status != null && flow != null)
                ? jobRepository.findByStatusAndFlow(JobStatus.valueOf(status), flow, pageable)
                : (status != null)
                    ? jobRepository.findByStatus(JobStatus.valueOf(status), pageable)
                    : (flow != null)
                        ? jobRepository.findByFlow(flow, pageable)
                        : jobRepository.findAll(pageable);
        return page.getContent().stream().map(AutomationDtos.JobDto::from).toList();
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<AutomationDtos.JobDto> getJob(@PathVariable UUID id) {
        return jobRepository.findById(id)
                .map(AutomationDtos.JobDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/jobs/trigger")
    public ResponseEntity<Map<String, Object>> trigger(@RequestBody Map<String, Object> body) {
        Object reservationId = body.get("reservationId");
        Object flow = body.get("flow");
        if (reservationId == null || flow == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "reservationId and flow are required"));
        }
        AutomationEventType type = "rental_reminder".equals(flow)
                ? AutomationEventType.RENTAL_UPCOMING
                : AutomationEventType.BOOKING_CONFIRMED;
        AutomationEngine.IngestResult result = engine.dispatch(
                type,
                String.valueOf(reservationId),
                "manual",
                Map.of("reservationId", reservationId, "flow", flow));
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "eventId", result.eventId(),
                "flowsTriggered", result.flowsTriggered()));
    }

    @GetMapping("/messages")
    public List<AutomationDtos.MessageDto> listMessages(
            @RequestParam(required = false) String templateId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        var pageable = PageRequest.of(0, capped, Sort.by(Sort.Direction.DESC, "sentAt"));
        var page = (templateId != null && status != null)
                ? messageRepository.findByTemplateIdAndStatus(templateId, MessageStatus.valueOf(status), pageable)
                : (templateId != null)
                    ? messageRepository.findByTemplateId(templateId, pageable)
                    : (status != null)
                        ? messageRepository.findByStatus(MessageStatus.valueOf(status), pageable)
                        : messageRepository.findAll(pageable);
        return page.getContent().stream().map(AutomationDtos.MessageDto::from).toList();
    }

    @GetMapping("/templates")
    public List<AutomationDtos.TemplateDto> listTemplates() {
        return TemplateRegistry.all().stream().map(AutomationDtos.TemplateDto::from).toList();
    }

    @GetMapping("/flows")
    public List<AutomationDtos.FlowDto> listFlows() {
        return flowRegistry.all().stream()
                .map(f -> new AutomationDtos.FlowDto(
                        f.name(), f.triggerEvent(), f.description(), f.actions().size()))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Stub for HMAC verification. To enable: set
     * {@code automation.webhook.shared-secret} in application.properties and
     * call into a HmacSigner helper here.
     */
    private void verifySignature(String signature, AutomationEventRequest req) {
        // No-op for now; production deployments should enable HMAC verification.
    }

    private static Map<String, Object> mergeMap(Map<String, Object> a, Map<String, Object> b) {
        java.util.Map<String, Object> m = new java.util.HashMap<>(a);
        m.putAll(b);
        return m;
    }
}
