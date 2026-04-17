package com.assine.billing.application.outbox;

import com.assine.billing.domain.outbox.model.OutboxEvent;
import com.assine.billing.domain.outbox.port.EventPublisher;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around {@link EventPublisher} whose only purpose is to host the
 * Resilience4j {@link Retry} and {@link CircuitBreaker} annotations on a public
 * method in a separate Spring bean, so the AOP proxy actually intercepts them.
 *
 * <p>Previously these annotations lived on a private method of
 * {@code OutboxEventPublisherService} and were never triggered because Spring
 * AOP does not advice self-invocations ({@code this.method()}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResilientEventPublisher {

    private final EventPublisher eventPublisher;

    @Retry(name = "sqsPublisher")
    @CircuitBreaker(name = "sqsPublisher", fallbackMethod = "publishFallback")
    public void publish(OutboxEvent event) {
        eventPublisher.publish(
                event.getEventId().toString(),
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId().toString(),
                event.getEventPayload()
        );
    }

    @SuppressWarnings("unused")
    private void publishFallback(OutboxEvent event, Throwable throwable) {
        log.warn("Circuit breaker open or persistent failure for event {} ({}): {}",
                event.getId(), event.getEventType(), throwable.getMessage());
        throw new EventPublishException(
                "Circuit breaker open or persistent failure for event: " + event.getId(),
                throwable);
    }

    public static class EventPublishException extends RuntimeException {
        public EventPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
