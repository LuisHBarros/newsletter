package com.assine.billing.adapters.outbound.messaging.sqs;

import com.assine.billing.domain.outbox.port.EventPublisher;
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

    @Value("${aws.sqs.subscriptions.queue:assine-subscriptions.fifo}")
    private String subscriptionsQueue;

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

            if (eventType != null && eventType.startsWith("billing.")) {
                publishToSubscriptionsQueue(eventId, eventType, envelope, aggregateType, aggregateId, payload);
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

    private void publishToSubscriptionsQueue(String eventId, String eventType, Map<String, Object> envelope,
                                              String aggregateType, String aggregateId, Map<String, Object> payload) {
        String messageGroupId = resolveMessageGroupId(aggregateType, aggregateId, payload);

        sqsTemplate.send(to -> to
            .queue(subscriptionsQueue)
            .payload(envelope)
            .messageGroupId(messageGroupId)
            .messageDeduplicationId(eventId)
            .header("AWSTraceHeader", getAwsTraceHeader()));

        log.info("Published billing event: {} (eventId: {}) to FIFO queue: {} with MessageGroupId={}",
            eventType, eventId, subscriptionsQueue, messageGroupId);
    }

    /**
     * Resolves the SQS FIFO MessageGroupId to guarantee ordering per subscription.
     *
     * Priority 1: payload.subscriptionId
     *   - For all billing.* events, subscriptionId in payload is the correct FIFO group key
     *   - For Subscription events, aggregateId = subscriptionId (same value)
     *   - For Payment events, aggregateId = paymentId (NOT subscriptionId) - using aggregateId would
     *     break ordering because each payment would get its own group
     *
     * Priority 2: aggregateId when aggregateType = Subscription
     *   - Fallback for Subscription events if subscriptionId is missing from payload
     *
     * Priority 3: aggregateId as last resort
     *   - Only reached for non-Subscription events without subscriptionId in payload
     */
    private String resolveMessageGroupId(String aggregateType, String aggregateId, Map<String, Object> payload) {
        Object subscriptionId = payload.get("subscriptionId");
        if (subscriptionId != null && !subscriptionId.toString().isEmpty()) {
            return subscriptionId.toString();
        }
        if ("Subscription".equals(aggregateType) && aggregateId != null && !aggregateId.isEmpty()) {
            return aggregateId;
        }
        log.warn("No subscriptionId found for billing.* event (aggregateType={}, aggregateId={}); using aggregateId as fallback",
            aggregateType, aggregateId);
        return aggregateId != null ? aggregateId : "unknown";
    }
}
