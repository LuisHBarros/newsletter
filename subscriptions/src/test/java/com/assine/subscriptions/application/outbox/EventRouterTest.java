package com.assine.subscriptions.application.outbox;

import com.assine.subscriptions.application.subscription.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EventRouterTest {

    @Mock
    private SubscriptionService subscriptionService;

    private EventRouter router;

    @BeforeEach
    void setUp() {
        router = new EventRouter(subscriptionService);
    }

    @Test
    void routesBillingActivatedToActivate() {
        UUID subscriptionId = UUID.randomUUID();
        Instant start = Instant.parse("2026-04-01T00:00:00Z");
        Instant end = Instant.parse("2026-05-01T00:00:00Z");

        router.consume("billing.subscription.activated", Map.of(
                "subscriptionId", subscriptionId.toString(),
                "currentPeriodStart", start.toString(),
                "currentPeriodEnd", end.toString()
        ));

        verify(subscriptionService).activate(subscriptionId, start, end);
    }

    @Test
    void routesPaymentSucceededToRenewPeriod() {
        UUID subscriptionId = UUID.randomUUID();
        Instant start = Instant.parse("2026-05-01T00:00:00Z");
        Instant end = Instant.parse("2026-06-01T00:00:00Z");

        router.consume("billing.payment.succeeded", Map.of(
                "subscriptionId", subscriptionId.toString(),
                "currentPeriodStart", start.toString(),
                "currentPeriodEnd", end.toString()
        ));

        verify(subscriptionService).renewPeriod(subscriptionId, start, end);
    }

    @Test
    void routesPaymentFailedToMarkPastDue() {
        UUID subscriptionId = UUID.randomUUID();

        router.consume("billing.payment.failed", Map.of("subscriptionId", subscriptionId.toString()));

        verify(subscriptionService).markPastDue(subscriptionId);
    }

    @Test
    void routesBillingCanceledToConfirmCanceled() {
        UUID subscriptionId = UUID.randomUUID();
        Instant canceledAt = Instant.parse("2026-05-10T00:00:00Z");

        router.consume("billing.subscription.canceled", Map.of(
                "subscriptionId", subscriptionId.toString(),
                "canceledAt", canceledAt.toString(),
                "reason", "user_requested"
        ));

        verify(subscriptionService).confirmCanceled(subscriptionId, canceledAt, "user_requested");
    }

    @Test
    void unknownEventTypeIsIgnored() {
        router.consume("billing.something.unknown", Map.of("subscriptionId", UUID.randomUUID().toString()));

        verifyNoInteractions(subscriptionService);
    }

    @Test
    void malformedPayloadPropagatesException() {
        assertThrows(IllegalArgumentException.class, () -> router.consume(
                "billing.subscription.activated",
                Map.of("subscriptionId", "not-a-uuid")
        ));
    }
}
