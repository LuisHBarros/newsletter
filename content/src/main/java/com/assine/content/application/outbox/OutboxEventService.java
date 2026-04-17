package com.assine.content.application.outbox;

import com.assine.content.domain.outbox.model.OutboxEvent;
import com.assine.content.domain.outbox.model.OutboxEventStatus;
import com.assine.content.domain.outbox.repository.OutboxEventRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public OutboxEvent createEvent(String eventType, String aggregateType, UUID aggregateId, Map<String, Object> payload) {
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventPayload(payload)
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build();
        return outboxEventRepository.save(event);
    }
}
