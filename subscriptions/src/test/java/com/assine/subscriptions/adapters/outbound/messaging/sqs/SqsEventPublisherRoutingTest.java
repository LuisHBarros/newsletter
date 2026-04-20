package com.assine.subscriptions.adapters.outbound.messaging.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsEventPublisherRoutingTest {

    @Mock
    private SqsTemplate sqsTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Tracer tracer;

    private SqsEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SqsEventPublisher(sqsTemplate, objectMapper, tracer);
        ReflectionTestUtils.setField(publisher, "eventsQueue", "assine-events");
        ReflectionTestUtils.setField(publisher, "billingQueue", "assine-billing");
    }

    @Test
    void subscriptionRequested_routedToBillingQueue() {
        publisher.publish("subscription.requested", Map.of("subscriptionId", "sub-1"));
        verify(sqsTemplate).send(any(Consumer.class));
    }

    @Test
    void subscriptionCancelRequested_routedToBillingQueue() {
        publisher.publish("subscription.cancel_requested", Map.of("subscriptionId", "sub-1"));
        verify(sqsTemplate).send(any(Consumer.class));
    }

    @Test
    void subscriptionActivated_routedToBillingQueue() {
        publisher.publish("subscription.activated", Map.of("subscriptionId", "sub-1"));
        verify(sqsTemplate).send(any(Consumer.class));
    }

    @Test
    void subscriptionExpired_routedToBillingQueue() {
        publisher.publish("subscription.expired", Map.of("subscriptionId", "sub-1"));
        verify(sqsTemplate).send(any(Consumer.class));
    }

    @Test
    void planCreated_routedToBillingQueue() {
        publisher.publish("plan.created", Map.of("planId", "plan-1"));
        verify(sqsTemplate).send(any(Consumer.class));
    }

    @Test
    void planUpdated_routedToBillingQueue() {
        publisher.publish("plan.updated", Map.of("planId", "plan-1"));
        verify(sqsTemplate).send(any(Consumer.class));
    }

    @Test
    void planDeleted_routedToBillingQueue() {
        publisher.publish("plan.deleted", Map.of("planId", "plan-1"));
        verify(sqsTemplate).send(any(Consumer.class));
    }

    @Test
    void billingEvent_routedToEventsQueue() {
        publisher.publish("billing.subscription.canceled", Map.of("subscriptionId", "sub-1"));
        verify(sqsTemplate).send(any(Consumer.class));
    }

    @Test
    void contentEvent_routedToEventsQueue() {
        publisher.publish("content.newsletter.created", Map.of("newsletterId", "nl-1"));
        verify(sqsTemplate).send(any(Consumer.class));
    }

    @Test
    void unknownEvent_routedToEventsQueue() {
        publisher.publish("some.other.event", Map.of("key", "value"));
        verify(sqsTemplate).send(any(Consumer.class));
    }

    @Test
    void nullEventType_routedToEventsQueue() {
        publisher.publish((String) null, Map.of("key", "value"));
        verify(sqsTemplate).send(any(Consumer.class));
    }
}
