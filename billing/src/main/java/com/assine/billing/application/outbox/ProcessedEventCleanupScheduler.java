package com.assine.billing.application.outbox;

import com.assine.billing.domain.outbox.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Periodically removes {@code processed_events} rows whose {@code expires_at}
 * has passed. The TTL (default 30 days) is set at insert time in
 * {@code ProcessedEvent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessedEventCleanupScheduler {

    private final ProcessedEventRepository processedEventRepository;

    @Scheduled(cron = "0 30 2 * * ?") // Daily at 02:30
    @SchedulerLock(name = "processed-events-cleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void cleanupExpired() {
        int deleted = processedEventRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired processed_events rows", deleted);
        }
    }
}
