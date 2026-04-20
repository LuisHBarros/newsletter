package com.assine.content.domain.outbox.repository;

import com.assine.content.domain.outbox.model.OutboxEvent;
import com.assine.content.domain.outbox.model.OutboxEventStatus;

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
    List<OutboxEvent> findDueForPublishing(int limit, Instant now);
    void markAsPublished(UUID id);
    void markAsFailed(UUID id, String error);
    void scheduleRetry(UUID id, Instant nextAttemptAt, String error);
    void delete(UUID id);
    long countByStatus(OutboxEventStatus status);
}
