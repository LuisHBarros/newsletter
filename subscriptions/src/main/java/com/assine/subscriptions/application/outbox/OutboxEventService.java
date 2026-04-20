package com.assine.subscriptions.application.outbox;

import com.assine.subscriptions.domain.outbox.model.OutboxEvent;
import com.assine.subscriptions.domain.outbox.model.OutboxEventStatus;
import com.assine.subscriptions.domain.outbox.repository.OutboxEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public OutboxEvent createEvent(String eventType, String aggregateType, UUID aggregateId, Map<String, Object> payload) {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventPayload(payload)
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build();
        return outboxEventRepository.save(event);
    }

    public OutboxEvent getEvent(UUID id) {
        return outboxEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found with id: " + id));
    }

    public List<OutboxEvent> getPendingEvents() {
        return outboxEventRepository.findByStatusOrderByCreatedAt(OutboxEventStatus.PENDING);
    }

    public List<OutboxEvent> getEventsByStatus(OutboxEventStatus status) {
        return outboxEventRepository.findByStatus(status);
    }

    public List<OutboxEvent> getEventsByAggregate(String aggregateType, UUID aggregateId) {
        return outboxEventRepository.findByAggregateTypeAndAggregateId(aggregateType, aggregateId);
    }

    @Transactional
    public void markAsPublished(UUID id) {
        if (!outboxEventRepository.findById(id).isPresent()) {
            throw new IllegalArgumentException("Outbox event not found with id: " + id);
        }
        outboxEventRepository.markAsPublished(id);
    }

    @Transactional
    public void markAsFailed(UUID id, String error) {
        if (!outboxEventRepository.findById(id).isPresent()) {
            throw new IllegalArgumentException("Outbox event not found with id: " + id);
        }
        outboxEventRepository.markAsFailed(id, error);
    }

    @Transactional
    public void incrementRetryCount(UUID id) {
        if (!outboxEventRepository.findById(id).isPresent()) {
            throw new IllegalArgumentException("Outbox event not found with id: " + id);
        }
        outboxEventRepository.incrementRetryCount(id);
    }

    @Transactional
    public void deleteEvent(UUID id) {
        if (!outboxEventRepository.findById(id).isPresent()) {
            throw new IllegalArgumentException("Outbox event not found with id: " + id);
        }
        outboxEventRepository.delete(id);
    }
}
