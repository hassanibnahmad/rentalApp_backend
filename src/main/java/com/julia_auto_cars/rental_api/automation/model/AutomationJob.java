package com.julia_auto_cars.rental_api.automation.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Tracking record for every flow run. One row per (event × flow).
 *
 * <p>Jobs are stored as plain rows in PostgreSQL. The {@code scheduler.DueJobDispatcher}
 * polls every few seconds, claims SCHEDULED jobs whose runAt is in the past,
 * and runs them in a worker thread. This makes the system fully restart-safe
 * without needing Redis or an external scheduler.</p>
 */
@Entity
@Table(
    name = "automation_jobs",
    indexes = {
        @Index(name = "idx_automation_job_flow", columnList = "flow"),
        @Index(name = "idx_automation_job_status", columnList = "status"),
        @Index(name = "idx_automation_job_run_at", columnList = "run_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutomationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** Flow name, e.g. "booking_abandoned". */
    @Column(nullable = false, length = 64)
    private String flow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    /**
     * 0-based index of the next action to run. Updated as actions complete.
     * Long delays inside a flow split the work: we save the resume index, set
     * runAt into the future, and let the scheduler pick it up again.
     */
    @Column(name = "action_index", nullable = false)
    private int actionIndex;

    /**
     * Total accumulated delay in milliseconds from all `delay` actions that
     * have been crossed so far in the current run.
     */
    @Column(name = "accumulated_delay_ms", nullable = false)
    private long accumulatedDelayMs;

    /** When the flow was scheduled to run (after all delays). */
    @Column(name = "run_at", nullable = false)
    private OffsetDateTime runAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    /** Snapshot of variables at scheduling time. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> variables;

    @Column(length = 2000)
    private String error;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
