package com.julia_auto_cars.rental_api.automation.scheduler;

import com.julia_auto_cars.rental_api.automation.model.AutomationJob;
import com.julia_auto_cars.rental_api.automation.model.JobStatus;
import com.julia_auto_cars.rental_api.automation.repository.AutomationJobRepository;
import com.julia_auto_cars.rental_api.automation.service.AutomationWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls the {@code automation_jobs} table for SCHEDULED jobs whose
 * {@code runAt} is in the past, claims them with a row-level lock, and
 * dispatches them to the worker pool.
 *
 * <p>This is the durable equivalent of BullMQ's delayed jobs: the schedule
 * lives in the database, so it survives restarts. The poll interval is
 * configurable via {@code automation.timing.dispatcher-poll-ms}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobDispatcher {

    private final AutomationJobRepository jobRepository;
    private final AutomationWorker worker;

    @Value("${automation.timing.dispatcher-poll-ms:5000}")
    private long pollMs;

    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "automation-worker");
        t.setDaemon(true);
        return t;
    });

    @Scheduled(fixedDelayString = "${automation.timing.dispatcher-poll-ms:5000}")
    @Transactional
    public void tick() {
        List<AutomationJob> due = jobRepository.findDueJobs(
                JobStatus.SCHEDULED, OffsetDateTime.now(), PageRequest.of(0, 10));
        if (due.isEmpty()) return;
        log.debug("dispatcher_tick due={}", due.size());
        for (AutomationJob job : due) {
            final UUID id = job.getId();
            pool.submit(() -> {
                try {
                    worker.run(id);
                } catch (Exception ex) {
                    log.error("worker_run_exception id={}", id, ex);
                }
            });
        }
    }
}
