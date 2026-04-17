package com.assine.billing.domain.outbox.port;

import java.util.Map;

public interface EventConsumer {
    void consume(String eventType, Map<String, Object> payload);
}
