package com.assine.content.application.outbox;

import com.assine.content.domain.outbox.model.OutboxEvent;
import com.assine.content.domain.outbox.port.EventPublisher;
import com.assine.content.domain.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Polls {@code outbox_events} for PENDING events due for publishing and pushes them
 * to SQS via {@link EventPublisher}. On failure, schedules an exponential-backoff retry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "content.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 8;

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;

    @Scheduled(cron = "${content.outbox.relay.cron:*/5 * * * * *}")
    @SchedulerLock(name = "content-outbox-relay", lockAtMostFor = "PT1M", lockAtLeastFor = "PT1S")
    public void drain() {
        List<OutboxEvent> due = outboxEventRepository.findDueForPublishing(BATCH_SIZE, Instant.now());
        if (due.isEmpty()) return;

        log.debug("Relaying {} outbox events", due.size());
        for (OutboxEvent e : due) {
            try {
                eventPublisher.publish(
                        e.getEventId().toString(),
                        e.getEventType(),
                        e.getAggregateType(),
                        e.getAggregateId() != null ? e.getAggregateId().toString() : null,
                        e.getEventPayload());
                outboxEventRepository.markAsPublished(e.getId());
            } catch (Exception ex) {
                log.warn("Failed to publish outbox event {} (retry {}): {}", e.getId(), e.getRetryCount(), ex.getMessage());
                int retry = e.getRetryCount() != null ? e.getRetryCount() : 0;
                if (retry >= MAX_RETRIES) {
                    outboxEventRepository.markAsFailed(e.getId(), ex.getMessage());
                } else {
                    Instant nextAttempt = Instant.now().plus(Duration.ofSeconds(backoffSeconds(retry)));
                    outboxEventRepository.scheduleRetry(e.getId(), nextAttempt, ex.getMessage());
                }
            }
        }
    }

    private long backoffSeconds(int retry) {
        // 1, 2, 4, 8, 16, 32, 64, 128 seconds
        return 1L << Math.min(retry, 7);
    }
}
