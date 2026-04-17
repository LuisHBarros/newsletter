package com.assine.billing.adapters.outbound.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsEventPublisherTest {

    @Mock
    private SqsTemplate sqsTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Captor
    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Consumer> sendOptionsCaptor;

    private SqsEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SqsEventPublisher(sqsTemplate, objectMapper);
        ReflectionTestUtils.setField(publisher, "eventsQueue", "assine-events");
        ReflectionTestUtils.setField(publisher, "subscriptionsQueue", "assine-subscriptions.fifo");
    }

    @Test
    void billingSubscriptionActivated_routedToFifoQueue() {
        String eventId = UUID.randomUUID().toString();
        String subscriptionId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of("subscriptionId", subscriptionId);

        publisher.publish(eventId, "billing.subscription.activated", "Subscription", subscriptionId, payload);

        verify(sqsTemplate).send(sendOptionsCaptor.capture());
        verify(sqsTemplate, never()).send(any(String.class), any());
    }

    @Test
    void billingPaymentSucceeded_extractsSubscriptionIdFromPayload() {
        String eventId = UUID.randomUUID().toString();
        String paymentId = UUID.randomUUID().toString();
        String subscriptionId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "paymentId", paymentId,
            "subscriptionId", subscriptionId
        );

        publisher.publish(eventId, "billing.payment.succeeded", "Payment", paymentId, payload);

        verify(sqsTemplate).send(sendOptionsCaptor.capture());
        verify(sqsTemplate, never()).send(any(String.class), any());
    }

    @Test
    void billingPaymentFailed_routedToFifoQueue() {
        String eventId = UUID.randomUUID().toString();
        String paymentId = UUID.randomUUID().toString();
        String subscriptionId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "paymentId", paymentId,
            "subscriptionId", subscriptionId,
            "attempts", 1,
            "reason", "card_declined"
        );

        publisher.publish(eventId, "billing.payment.failed", "Payment", paymentId, payload);

        verify(sqsTemplate).send(sendOptionsCaptor.capture());
        verify(sqsTemplate, never()).send(any(String.class), any());
    }

    @Test
    void billingSubscriptionCanceled_routedToFifoQueue() {
        String eventId = UUID.randomUUID().toString();
        String subscriptionId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "subscriptionId", subscriptionId,
            "canceledAt", "2026-04-17T00:00:00Z",
            "reason", "user_requested"
        );

        publisher.publish(eventId, "billing.subscription.canceled", "Subscription", subscriptionId, payload);

        verify(sqsTemplate).send(sendOptionsCaptor.capture());
        verify(sqsTemplate, never()).send(any(String.class), any());
    }

    @Test
    void nonBillingEvent_routedToStandardQueue() {
        Map<String, Object> payload = Map.of("key", "value");

        publisher.publish("some.other.event", "Other", "id", payload);

        verify(sqsTemplate).send(eq("assine-events"), any(Map.class));
        verify(sqsTemplate, never()).send(any(Consumer.class));
    }

    @Test
    void nonBillingEventType_routedToStandardQueue() {
        Map<String, Object> payload = Map.of("key", "value");

        publisher.publish("eventId", "content.newsletter.created", "Newsletter", "id", payload);

        verify(sqsTemplate).send(eq("assine-events"), any(Map.class));
        verify(sqsTemplate, never()).send(any(Consumer.class));
    }

    @Test
    void billingEventWithMissingSubscriptionId_usesAggregateIdFallback() {
        String eventId = UUID.randomUUID().toString();
        String subscriptionId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of();

        publisher.publish(eventId, "billing.subscription.activated", "Subscription", subscriptionId, payload);

        verify(sqsTemplate).send(sendOptionsCaptor.capture());
        verify(sqsTemplate, never()).send(any(String.class), any());
    }

    @Test
    void publishFailure_throwsRuntimeException() {
        when(sqsTemplate.send(any(String.class), any())).thenThrow(new RuntimeException("SQS down"));

        assertThatThrownBy(() -> publisher.publish("some.event", Map.of()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to publish event");
    }
}
