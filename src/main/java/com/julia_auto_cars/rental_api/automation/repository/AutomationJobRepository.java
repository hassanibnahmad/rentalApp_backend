package com.julia_auto_cars.rental_api.automation.repository;

import com.julia_auto_cars.rental_api.automation.model.AutomationJob;
import com.julia_auto_cars.rental_api.automation.model.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutomationJobRepository extends JpaRepository<AutomationJob, UUID> {

    /**
     * Claim the next batch of SCHEDULED jobs that are due to run.
     * Uses PESSIMISTIC_WRITE so two scheduler ticks can't grab the same row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT j FROM AutomationJob j
        WHERE j.status = :status AND j.runAt <= :now
        ORDER BY j.runAt ASC
        """)
    List<AutomationJob> findDueJobs(@Param("status") JobStatus status,
                                    @Param("now") OffsetDateTime now,
                                    Pageable pageable);

    Page<AutomationJob> findByStatus(JobStatus status, Pageable pageable);

    Page<AutomationJob> findByFlow(String flow, Pageable pageable);

    Page<AutomationJob> findByStatusAndFlow(JobStatus status, String flow, Pageable pageable);

    Optional<AutomationJob> findFirstByEventIdAndFlow(UUID eventId, String flow);
}
