package com.julia_auto_cars.rental_api.automation.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Audit log of every WhatsApp message we send (or try to send).
 */
@Entity
@Table(
    name = "whatsapp_messages",
    indexes = {
        @Index(name = "idx_wa_msg_template", columnList = "template_id"),
        @Index(name = "idx_wa_msg_status", columnList = "status"),
        @Index(name = "idx_wa_msg_to_phone", columnList = "to_phone"),
        @Index(name = "idx_wa_msg_provider_id", columnList = "provider_message_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id")
    private UUID jobId;

    /** Template id from the spec, e.g. "booking_confirmation". */
    @Column(name = "template_id", nullable = false, length = 64)
    private String templateId;

    /** Resolved final message body (after interpolation). */
    @Column(nullable = false, length = 4000)
    private String body;

    /** Phone number we sent to (E.164). */
    @Column(name = "to_phone", nullable = false, length = 32)
    private String toPhone;

    /** Meta message id (if delivery was accepted). */
    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageStatus status;

    @Column(name = "provider_error", length = 1000)
    private String providerError;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (sentAt == null) sentAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
