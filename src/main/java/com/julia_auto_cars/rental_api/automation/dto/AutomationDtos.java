package com.julia_auto_cars.rental_api.automation.dto;

import com.julia_auto_cars.rental_api.automation.model.AutomationEvent;
import com.julia_auto_cars.rental_api.automation.model.AutomationEventType;
import com.julia_auto_cars.rental_api.automation.model.AutomationJob;
import com.julia_auto_cars.rental_api.automation.model.JobStatus;
import com.julia_auto_cars.rental_api.automation.model.MessageStatus;
import com.julia_auto_cars.rental_api.automation.model.WhatsAppMessage;
import com.julia_auto_cars.rental_api.automation.template.TemplateRegistry;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the admin monitoring API. Kept stable so the React admin
 * dashboard can consume them.
 */
public final class AutomationDtos {

    private AutomationDtos() {}

    public record EventDto(
            UUID id,
            AutomationEventType type,
            String source,
            String externalId,
            boolean processed,
            int flowCount,
            String error,
            OffsetDateTime receivedAt,
            OffsetDateTime processedAt
    ) {
        public static EventDto from(AutomationEvent e) {
            return new EventDto(e.getId(), e.getType(), e.getSource(), e.getExternalId(),
                    e.isProcessed(), e.getFlowCount(), e.getError(), e.getReceivedAt(), e.getProcessedAt());
        }
    }

    public record JobDto(
            UUID id,
            UUID eventId,
            String flow,
            JobStatus status,
            int actionIndex,
            int attempts,
            OffsetDateTime runAt,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            String error
    ) {
        public static JobDto from(AutomationJob j) {
            return new JobDto(j.getId(), j.getEventId(), j.getFlow(), j.getStatus(),
                    j.getActionIndex(), j.getAttempts(), j.getRunAt(), j.getStartedAt(),
                    j.getFinishedAt(), j.getError());
        }
    }

    public record MessageDto(
            UUID id,
            UUID jobId,
            String templateId,
            String body,
            String toPhone,
            String providerMessageId,
            MessageStatus status,
            String providerError,
            OffsetDateTime sentAt,
            OffsetDateTime deliveredAt,
            OffsetDateTime readAt
    ) {
        public static MessageDto from(WhatsAppMessage m) {
            return new MessageDto(m.getId(), m.getJobId(), m.getTemplateId(), m.getBody(),
                    m.getToPhone(), m.getProviderMessageId(), m.getStatus(),
                    m.getProviderError(), m.getSentAt(), m.getDeliveredAt(), m.getReadAt());
        }
    }

    public record TemplateDto(String id, List<String> variables, String body) {
        public static TemplateDto from(TemplateRegistry.Template t) {
            return new TemplateDto(t.id(), t.variablePaths(), t.body());
        }
    }

    public record FlowDto(String name, String triggerEvent, String description, int actionCount) {}
}
