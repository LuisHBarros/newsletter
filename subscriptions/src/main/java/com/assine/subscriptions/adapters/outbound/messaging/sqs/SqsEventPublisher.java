package com.assine.subscriptions.adapters.outbound.messaging.sqs;

import com.assine.subscriptions.domain.outbox.port.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.micrometer.tracing.Tracer;
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
    private final Tracer tracer;

    @Value("${aws.sqs.events.queue:assine-events}")
    private String eventsQueue;

    @Value("${aws.sqs.billing.queue:assine-billing}")
    private String billingQueue;

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
                "eventType", eventType != null ? eventType : "",
                "aggregateType", aggregateType != null ? aggregateType : "",
                "aggregateId", aggregateId != null ? aggregateId : "",
                "payload", payload
            );

            if (eventType != null && (eventType.startsWith("subscription.") || eventType.startsWith("plan."))) {
                sqsTemplate.send(to -> to
                    .queue(billingQueue)
                    .payload(envelope)
                    .header("AWSTraceHeader", getAwsTraceHeader()));
                log.info("Published event: {} (eventId: {}) to billing queue: {}", eventType, eventId, billingQueue);
            } else {
                sqsTemplate.send(to -> to
                    .queue(eventsQueue)
                    .payload(envelope)
                    .header("AWSTraceHeader", getAwsTraceHeader()));
                log.info("Published event: {} (eventId: {}) to queue: {}", eventType, eventId, eventsQueue);
            }
        } catch (Exception e) {
            log.error("Failed to publish event: {} (eventId: {})", eventType, eventId, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    private String getAwsTraceHeader() {
        var currentSpan = tracer.currentSpan();
        if (currentSpan != null && currentSpan.context() != null) {
            var traceId = currentSpan.context().traceId();
            var spanId = currentSpan.context().spanId();
            return String.format("Root=%s;Parent=%s;Sampled=1", traceId, spanId);
        }
        return "";
    }
}
