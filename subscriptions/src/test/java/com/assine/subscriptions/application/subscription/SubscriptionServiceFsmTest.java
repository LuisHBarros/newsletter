package com.assine.subscriptions.application.subscription;

import com.assine.subscriptions.application.outbox.OutboxEventService;
import com.assine.subscriptions.domain.plan.model.BillingInterval;
import com.assine.subscriptions.domain.plan.model.Plan;
import com.assine.subscriptions.domain.plan.repository.PlanRepository;
import com.assine.subscriptions.domain.subscription.exception.IllegalStateTransitionException;
import com.assine.subscriptions.domain.subscription.model.Subscription;
import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;
import com.assine.subscriptions.domain.subscription.repository.SubscriptionRepository;
import com.assine.subscriptions.domain.subscription.service.SubscriptionStateGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceFsmTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private OutboxEventService outboxEventService;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                subscriptionRepository,
                planRepository,
                outboxEventService,
                new SubscriptionStateGuard()
        );
    }

    @Test
    void shouldActivateFromPendingPayment() {
        Subscription subscription = buildSubscription(SubscriptionStatus.PENDING_PAYMENT);
        Instant start = Instant.parse("2026-04-01T00:00:00Z");
        Instant end = Instant.parse("2026-05-01T00:00:00Z");

        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        Subscription result = subscriptionService.activate(subscription.getId(), start, end);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(outboxEventService).createEvent(eq("subscription.activated"), eq("Subscription"), eq(subscription.getId()), any(Map.class));
    }

    @Test
    void shouldRejectActivateFromCanceled() {
        Subscription subscription = buildSubscription(SubscriptionStatus.CANCELED);
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        assertThrows(IllegalStateTransitionException.class,
                () -> subscriptionService.activate(subscription.getId(), Instant.now(), Instant.now().plusSeconds(3600)));
    }

    @Test
    void shouldMarkPastDueFromActive() {
        Subscription subscription = buildSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        Subscription result = subscriptionService.markPastDue(subscription.getId());

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        verify(outboxEventService).createEvent(eq("subscription.past_due"), eq("Subscription"), eq(subscription.getId()), any(Map.class));
    }

    @Test
    void shouldRejectMarkPastDueFromPendingPayment() {
        Subscription subscription = buildSubscription(SubscriptionStatus.PENDING_PAYMENT);
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        assertThrows(IllegalStateTransitionException.class,
                () -> subscriptionService.markPastDue(subscription.getId()));
    }

    @Test
    void shouldRenewPeriodFromPastDueToActive() {
        Subscription subscription = buildSubscription(SubscriptionStatus.PAST_DUE);
        Instant start = Instant.parse("2026-05-01T00:00:00Z");
        Instant end = Instant.parse("2026-06-01T00:00:00Z");

        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        Subscription result = subscriptionService.renewPeriod(subscription.getId(), start, end);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getCurrentPeriodStart()).isEqualTo(start);
        assertThat(result.getCurrentPeriodEnd()).isEqualTo(end);
    }

    @Test
    void shouldExpireFromActiveAndPublishEvent() {
        Subscription subscription = buildSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        Subscription result = subscriptionService.expire(subscription.getId());

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).createEvent(eq("subscription.expired"), eq("Subscription"), eq(subscription.getId()), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).containsKeys("subscriptionId", "userId", "planId", "expiredAt");
    }

    @Test
    void shouldRejectExpireFromCanceled() {
        Subscription subscription = buildSubscription(SubscriptionStatus.CANCELED);
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        assertThrows(IllegalStateTransitionException.class,
                () -> subscriptionService.expire(subscription.getId()));
    }

    private Subscription buildSubscription(SubscriptionStatus status) {
        Plan plan = Plan.builder()
                .id(UUID.randomUUID())
                .name("Plan")
                .description("Plan")
                .price(new BigDecimal("29.90"))
                .currency("BRL")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(0)
                .features(Map.of())
                .active(true)
                .build();

        return Subscription.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .plan(plan)
                .status(status)
                .cancelAtPeriodEnd(false)
                .metadata(Map.of())
                .build();
    }
}
