package com.julia_auto_cars.rental_api.automation.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Inbox of every event the automation service receives.
 * Deduplication key: (source, externalId, type).
 */
@Entity
@Table(
    name = "automation_events",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_automation_event_dedupe",
        columnNames = {"source", "external_id", "type"}
    ),
    indexes = {
        @Index(name = "idx_automation_event_type", columnList = "type"),
        @Index(name = "idx_automation_event_processed", columnList = "processed")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutomationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AutomationEventType type;

    /** "webhook" | "polling" | "manual" | "cron" */
    @Column(nullable = false, length = 32)
    private String source;

    /** External ID from the emitter (reservation.id) used for dedupe. */
    @Column(name = "external_id", length = 64)
    private String externalId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(nullable = false)
    private boolean processed;

    @Column(name = "flow_count", nullable = false)
    private int flowCount;

    @Column(length = 2000)
    private String error;

    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @PrePersist
    void onCreate() {
        if (receivedAt == null) receivedAt = OffsetDateTime.now();
    }
}
