package com.assine.subscriptions.adapters.outbound.messaging.sqs;

import com.assine.subscriptions.domain.outbox.port.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsEventPublisher implements EventPublisher {

    private static final int SCHEMA_VERSION = 1;

    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.events.queue:assine-events}")
    private String eventsQueue;

    @Override
    public void publish(String eventType, Map<String, Object> payload) {
        publish(eventType, null, null, payload);
    }

    @Override
    public void publish(String eventType, String aggregateType, String aggregateId, Map<String, Object> payload) {
        String eventId = UUID.randomUUID().toString();
        publish(eventId, eventType, aggregateType, aggregateId, payload);
    }

    @Override
    public void publish(String eventId, String eventType, String aggregateType, String aggregateId, Map<String, Object> payload) {
        try {
            Map<String, Object> envelope = Map.of(
                "eventId", eventId,
                "schemaVersion", SCHEMA_VERSION,
                "occurredAt", Instant.now().toString(),
                "eventType", eventType,
                "aggregateType", aggregateType != null ? aggregateType : "",
                "aggregateId", aggregateId != null ? aggregateId : "",
                "payload", payload
            );

            sqsTemplate.send(eventsQueue, envelope);
            log.info("Published event: {} (eventId: {}) to queue: {}", eventType, eventId, eventsQueue);
        } catch (Exception e) {
            log.error("Failed to publish event: {} (eventId: {}) to queue: {}", eventType, eventId, eventsQueue, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
