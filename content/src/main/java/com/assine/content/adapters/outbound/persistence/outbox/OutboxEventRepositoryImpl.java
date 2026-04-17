package com.assine.content.adapters.outbound.persistence.outbox;

import com.assine.content.domain.outbox.model.OutboxEvent;
import com.assine.content.domain.outbox.model.OutboxEventStatus;
import com.assine.content.domain.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpa;

    @Override
    @Transactional
    public OutboxEvent save(OutboxEvent event) {
        return jpa.save(event);
    }

    @Override
    public Optional<OutboxEvent> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<OutboxEvent> findByStatus(OutboxEventStatus status) {
        return jpa.findByStatus(status);
    }

    @Override
    public List<OutboxEvent> findByStatusOrderByCreatedAt(OutboxEventStatus status) {
        return jpa.findByStatusOrderByCreatedAtAsc(status);
    }

    @Override
    public List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId) {
        return jpa.findByAggregateTypeAndAggregateId(aggregateType, aggregateId);
    }

    @Override
    public List<OutboxEvent> findDueForPublishing(int limit, Instant now) {
        return jpa.findDueForPublishing(now, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public void markAsPublished(UUID id) {
        jpa.markAsPublished(id);
    }

    @Override
    @Transactional
    public void markAsFailed(UUID id, String error) {
        jpa.markAsFailed(id, error);
    }

    @Override
    @Transactional
    public void scheduleRetry(UUID id, Instant nextAttemptAt, String error) {
        jpa.scheduleRetry(id, nextAttemptAt, error);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        jpa.deleteById(id);
    }
}
