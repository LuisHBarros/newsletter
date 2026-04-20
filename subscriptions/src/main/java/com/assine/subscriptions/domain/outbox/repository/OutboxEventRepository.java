package com.assine.subscriptions.domain.outbox.repository;

import com.assine.subscriptions.domain.outbox.model.OutboxEvent;
import com.assine.subscriptions.domain.outbox.model.OutboxEventStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent event);
    Optional<OutboxEvent> findById(UUID id);
    List<OutboxEvent> findByStatus(OutboxEventStatus status);
    List<OutboxEvent> findByStatusOrderByCreatedAt(OutboxEventStatus status);
    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId);
    void delete(UUID id);
    void markAsPublished(UUID id);
    void markAsFailed(UUID id, String error);
    int incrementRetryCount(UUID id);
    int deleteOldPublishedEvents(Instant cutoffDate);
    long countByStatus(OutboxEventStatus status);

    /**
     * Find pending events that are due for publishing (nextAttemptAt <= now).
     * Results are limited and ordered by createdAt.
     */
    List<OutboxEvent> findDueForPublishing(int limit, Instant now);

    /**
     * Schedule a retry for a failed event with exponential backoff.
     * Increments retry count, sets nextAttemptAt, and stores error message.
     */
    void scheduleRetry(UUID id, Instant nextAttemptAt, String error);
}
