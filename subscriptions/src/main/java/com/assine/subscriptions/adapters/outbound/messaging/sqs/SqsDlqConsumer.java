package com.assine.subscriptions.adapters.outbound.messaging.sqs;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumer for Dead Letter Queues (DLQ).
 * Monitors messages that failed processing after max retries and emits CloudWatch metrics.
 */
@Slf4j
@Component
public class SqsDlqConsumer {

    private static final String DLQ_METRIC_NAME = "sqs.dlq.messages";
    private static final String QUEUE_TAG = "queue";

    private final MeterRegistry meterRegistry;
    private final String eventsDlqName;
    private final String subscriptionsDlqName;

    public SqsDlqConsumer(
            MeterRegistry meterRegistry,
            @Value("${aws.sqs.events.dlq:assine-events-dlq}") String eventsDlqName,
            @Value("${aws.sqs.subscriptions.dlq:assine-subscriptions-dlq.fifo}") String subscriptionsDlqName) {
        this.meterRegistry = meterRegistry;
        this.eventsDlqName = eventsDlqName;
        this.subscriptionsDlqName = subscriptionsDlqName;
    }

    /**
     * Listen to events DLQ (subscriptions service published events that failed).
     */
    @SqsListener(queueNames = "${aws.sqs.events.dlq:assine-events-dlq}")
    public void consumeEventsDlq(
            Map<String, Object> message,
            @Header(value = "id", required = false) String messageId,
            @Header(value = "ApproximateReceiveCount", required = false) String approximateReceiveCount,
            @Header(value = "SentTimestamp", required = false) String sentTimestamp) {

        consumeDlqMessage(message, messageId, approximateReceiveCount, sentTimestamp, eventsDlqName);
    }

    /**
     * Listen to subscriptions DLQ (billing -> subscriptions events that failed).
     * This is a FIFO queue with MessageGroupId=subscriptionId.
     */
    @SqsListener(queueNames = "${aws.sqs.subscriptions.dlq:assine-subscriptions-dlq.fifo}")
    public void consumeSubscriptionsDlq(
            Map<String, Object> message,
            @Header(value = "id", required = false) String messageId,
            @Header(value = "ApproximateReceiveCount", required = false) String approximateReceiveCount,
            @Header(value = "SentTimestamp", required = false) String sentTimestamp,
            @Header(value = "MessageGroupId", required = false) String messageGroupId) {

        String eventType = message.get("eventType") != null ? (String) message.get("eventType") : "unknown";
        log.error("DLQ message received: queue={}, messageId={}, messageGroupId={}, eventType={}, " +
                  "approximateReceiveCount={}, sentTimestamp={}, payload={}",
                subscriptionsDlqName, messageId, messageGroupId, eventType, approximateReceiveCount, sentTimestamp, message);

        incrementDlqCounter(subscriptionsDlqName);
    }

    private void consumeDlqMessage(
            Map<String, Object> message,
            String messageId,
            String approximateReceiveCount,
            String sentTimestamp,
            String queueName) {

        String eventType = message.get("eventType") != null ? (String) message.get("eventType") : "unknown";

        log.error("DLQ message received: queue={}, messageId={}, eventType={}, " +
                  "approximateReceiveCount={}, sentTimestamp={}, payload={}",
                queueName, messageId, eventType, approximateReceiveCount, sentTimestamp, message);

        incrementDlqCounter(queueName);
    }

    private void incrementDlqCounter(String queue) {
        Counter.builder(DLQ_METRIC_NAME)
                .tag(QUEUE_TAG, queue)
                .register(meterRegistry)
                .increment();
    }
}
