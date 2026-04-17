package com.assine.billing.adapters.outbound.messaging.sqs;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Monitors billing DLQs (events DLQ and inbound DLQ) and emits CloudWatch-friendly
 * counters. Same pattern as the subscriptions service, scoped to billing queues.
 */
@Slf4j
@Component
public class SqsDlqConsumer {

    private static final String DLQ_METRIC_NAME = "sqs.dlq.messages";
    private static final String QUEUE_TAG = "queue";

    private final MeterRegistry meterRegistry;
    private final String eventsDlqName;
    private final String inboundDlqName;

    public SqsDlqConsumer(
            MeterRegistry meterRegistry,
            @Value("${aws.sqs.events.dlq:assine-events-dlq}") String eventsDlqName,
            @Value("${aws.sqs.inbound.dlq:assine-billing-dlq}") String inboundDlqName) {
        this.meterRegistry = meterRegistry;
        this.eventsDlqName = eventsDlqName;
        this.inboundDlqName = inboundDlqName;
    }

    @SqsListener(queueNames = "${aws.sqs.events.dlq:assine-events-dlq}")
    public void consumeEventsDlq(
            Map<String, Object> message,
            @Header(value = "id", required = false) String messageId,
            @Header(value = "ApproximateReceiveCount", required = false) String approximateReceiveCount,
            @Header(value = "SentTimestamp", required = false) String sentTimestamp) {
        consumeDlqMessage(message, messageId, approximateReceiveCount, sentTimestamp, eventsDlqName);
    }

    @SqsListener(queueNames = "${aws.sqs.inbound.dlq:assine-billing-dlq}")
    public void consumeInboundDlq(
            Map<String, Object> message,
            @Header(value = "id", required = false) String messageId,
            @Header(value = "ApproximateReceiveCount", required = false) String approximateReceiveCount,
            @Header(value = "SentTimestamp", required = false) String sentTimestamp) {
        consumeDlqMessage(message, messageId, approximateReceiveCount, sentTimestamp, inboundDlqName);
    }

    private void consumeDlqMessage(Map<String, Object> message, String messageId,
                                    String approximateReceiveCount, String sentTimestamp, String queueName) {
        String eventType = message.get("eventType") != null ? (String) message.get("eventType") : "unknown";
        log.error("DLQ message received: queue={}, messageId={}, eventType={}, receiveCount={}, sentTimestamp={}, payload={}",
                queueName, messageId, eventType, approximateReceiveCount, sentTimestamp, message);
        Counter.builder(DLQ_METRIC_NAME)
                .tag(QUEUE_TAG, queueName)
                .register(meterRegistry)
                .increment();
    }
}
