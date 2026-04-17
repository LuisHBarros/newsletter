package com.assine.subscriptions.domain.outbox.port;

import java.util.Map;

public interface EventPublisher {
    void publish(String eventType, Map<String, Object> payload);
    void publish(String eventType, String aggregateType, String aggregateId, Map<String, Object> payload);
    void publish(String eventId, String eventType, String aggregateType, String aggregateId, Map<String, Object> payload);
}
