package com.assine.content.application.outbox;

import com.assine.content.domain.outbox.model.OutboxEvent;
import com.assine.content.domain.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Service responsible for publishing individual outbox events with:
 * - Per-event transaction (REQUIRES_NEW)
 * - In-process retry with exponential backoff via Resilience4j
 * - Circuit breaker for SQS failures
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisherService {

    private final ResilientEventPublisher resilientEventPublisher;
    private final OutboxEventRepository outboxEventRepository;

    private static final int MAX_RETRY_COUNT = 3;
    private static final Duration BASE_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_DELAY = Duration.ofMinutes(10);

    /**
     * Publish a single event with its own transaction.
     * Uses Resilience4j @Retry and @CircuitBreaker for transient failures.
     * On persistent failure, schedules a retry with exponential backoff in the database.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishWithRetry(OutboxEvent event) {
        try {
            resilientEventPublisher.publish(event);

            outboxEventRepository.markAsPublished(event.getId());
            log.info("Successfully published outbox event: {}", event.getId());
        } catch (Exception e) {
            log.error("Failed to publish outbox event after retries: {}", event.getId(), e);

            // Get current retry count from event (may be stale, but we increment in DB)
            int currentRetryCount = event.getRetryCount() != null ? event.getRetryCount() : 0;
            int newRetryCount = currentRetryCount + 1;

            if (newRetryCount >= MAX_RETRY_COUNT) {
                outboxEventRepository.markAsFailed(event.getId(), e.getMessage());
                log.error("Outbox event marked as failed after {} retries: {}", newRetryCount, event.getId());
            } else {
                // Calculate exponential backoff: base * 2^retryCount, capped at MAX_DELAY
                Duration backoff = calculateBackoff(newRetryCount);
                Instant nextAttemptAt = Instant.now().plus(backoff);

                outboxEventRepository.scheduleRetry(event.getId(), nextAttemptAt, e.getMessage());
                log.warn("Scheduled retry {} for outbox event: {} at {} (backoff: {}s)",
                        newRetryCount, event.getId(), nextAttemptAt, backoff.getSeconds());
            }
        }
    }

    private static final int MAX_SAFE_SHIFT = 62;

    /**
     * Calculate exponential backoff delay.
     * Formula: baseDelay * 2^retryCount, capped at maxDelay.
     * retryCount is clamped to MAX_SAFE_SHIFT to prevent long-shift overflow
     * on corrupted inputs.
     */
    private Duration calculateBackoff(int retryCount) {
        int safeRetryCount = Math.min(retryCount, MAX_SAFE_SHIFT);
        long delayMillis = BASE_DELAY.toMillis() * (1L << safeRetryCount);
        long cappedMillis = Math.min(delayMillis, MAX_DELAY.toMillis());
        return Duration.ofMillis(cappedMillis);
    }
}
