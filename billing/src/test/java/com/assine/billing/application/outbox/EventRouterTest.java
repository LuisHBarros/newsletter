package com.assine.billing.application.outbox;

import com.assine.billing.application.customer.BillingCustomerService;
import com.assine.billing.application.customer.BillingSubscriptionService;
import com.assine.billing.application.payment.CreatePaymentService;
import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.model.BillingSubscription;
import com.assine.billing.domain.customer.model.BillingSubscriptionStatus;
import com.assine.billing.domain.customer.repository.BillingSubscriptionRepository;
import com.assine.billing.domain.plan.model.BillingPlan;
import com.assine.billing.domain.plan.repository.BillingPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventRouterTest {

    @Mock private BillingCustomerService customerService;
    @Mock private BillingSubscriptionService billingSubscriptionService;
    @Mock private BillingSubscriptionRepository billingSubscriptionRepository;
    @Mock private CreatePaymentService createPaymentService;
    @Mock private OutboxEventService outboxEventService;
    @Mock private BillingPlanRepository planRepository;

    private EventRouter router;

    private BillingCustomer customer;
    private BillingSubscription subscription;
    private UUID subscriptionId;
    private UUID userId;
    private UUID planId;

    @BeforeEach
    void setUp() {
        router = new EventRouter(customerService, billingSubscriptionService,
                billingSubscriptionRepository, createPaymentService, outboxEventService, planRepository);

        subscriptionId = UUID.randomUUID();
        userId = UUID.randomUUID();
        planId = UUID.randomUUID();

        customer = BillingCustomer.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .provider("FAKE")
                .build();

        subscription = BillingSubscription.builder()
                .id(UUID.randomUUID())
                .subscriptionId(subscriptionId)
                .customer(customer)
                .planId(planId)
                .status(BillingSubscriptionStatus.PENDING)
                .providerSubscriptionRef("prov_ref")
                .currentPeriodStart(Instant.parse("2026-04-01T00:00:00Z"))
                .currentPeriodEnd(Instant.parse("2026-05-01T00:00:00Z"))
                .billingInterval("MONTHLY")
                .build();
    }

    private Map<String, Object> requestedPayload(String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", subscriptionId.toString());
        payload.put("userId", userId.toString());
        payload.put("planId", planId.toString());
        if (status != null) payload.put("status", status);
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("price", new BigDecimal("29.90"));
        snapshot.put("currency", "BRL");
        snapshot.put("billingInterval", "MONTHLY");
        snapshot.put("trialDays", 14);
        payload.put("planSnapshot", snapshot);
        return payload;
    }

    @Test
    void subscriptionRequestedProvisionsAndRegistersChargeWithIdempotencyKey() {
        when(customerService.findOrCreate(userId)).thenReturn(customer);
        when(billingSubscriptionService.findOrCreate(eq(subscriptionId), eq(customer), eq(planId), eq("MONTHLY")))
                .thenReturn(subscription);

        UUID eventId = UUID.randomUUID();
        router.consume("subscription.requested", requestedPayload(null), eventId);

        verify(createPaymentService).execute(
                eq(customer.getId()),
                eq(subscription),
                eq(new BigDecimal("29.90")),
                eq("BRL"),
                eq(eventId.toString()));
        // Async contract: activation is NOT set here — it must wait for the webhook.
        verify(billingSubscriptionService, never()).markActive(any());
        verifyNoInteractions(outboxEventService);
    }

    @Test
    void subscriptionRequestedTrialActivatesImmediatelyAndSkipsCharge() {
        when(customerService.findOrCreate(userId)).thenReturn(customer);
        when(billingSubscriptionService.findOrCreate(eq(subscriptionId), eq(customer), eq(planId), eq("MONTHLY")))
                .thenReturn(subscription);

        router.consume("subscription.requested", requestedPayload("TRIAL"), UUID.randomUUID());

        verify(billingSubscriptionService).markTrialActive(subscription, 14);
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).createEvent(
                eq("billing.subscription.activated"),
                eq("Subscription"),
                eq(subscriptionId),
                captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("currentPeriodStart")).isNotNull();
        assertThat(payload.get("currentPeriodEnd")).isNotNull();
        verifyNoInteractions(createPaymentService);
    }

    @Test
    void subscriptionRequestedDoesNotRethrowWhenProviderCreationFailsSync() {
        when(customerService.findOrCreate(userId)).thenReturn(customer);
        when(billingSubscriptionService.findOrCreate(any(), any(), any(), anyString())).thenReturn(subscription);
        when(createPaymentService.execute(any(), any(), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("card_declined"));

        // Must not escape — EventRouter logs and swallows so the SQS message isn't retried forever.
        router.consume("subscription.requested", requestedPayload(null), UUID.randomUUID());

        verify(billingSubscriptionService, never()).markActive(any());
    }

    @Test
    void subscriptionCancelRequestedMarksCanceledAndPublishes() {
        when(billingSubscriptionRepository.findBySubscriptionId(subscriptionId))
                .thenReturn(Optional.of(subscription));

        Map<String, Object> payload = Map.of("subscriptionId", subscriptionId.toString());
        router.consume("subscription.cancel_requested", payload, UUID.randomUUID());

        verify(billingSubscriptionService).markCanceled(eq(subscription), any(Instant.class));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).createEvent(
                eq("billing.subscription.canceled"),
                eq("Subscription"),
                eq(subscriptionId),
                captor.capture());
        assertThat(captor.getValue()).containsEntry("subscriptionId", subscriptionId.toString());
        assertThat(captor.getValue()).containsEntry("reason", "user_requested");
        assertThat(captor.getValue()).containsKey("canceledAt");
    }

    @Test
    void subscriptionCancelRequestedWithMissingSubscriptionIsNoop() {
        when(billingSubscriptionRepository.findBySubscriptionId(subscriptionId))
                .thenReturn(Optional.empty());

        router.consume("subscription.cancel_requested",
                Map.of("subscriptionId", subscriptionId.toString()),
                UUID.randomUUID());

        verifyNoInteractions(billingSubscriptionService);
        verifyNoInteractions(outboxEventService);
    }

    @Test
    void unknownEventTypeIsIgnored() {
        router.consume("billing.something.unknown",
                Map.of("subscriptionId", UUID.randomUUID().toString()),
                UUID.randomUUID());

        verifyNoInteractions(customerService, billingSubscriptionService,
                billingSubscriptionRepository, createPaymentService, outboxEventService, planRepository);
    }

    @Test
    void malformedPayloadPropagatesException() {
        assertThatThrownBy(() -> router.consume(
                "subscription.requested",
                Map.of("subscriptionId", "not-a-uuid",
                        "userId", UUID.randomUUID().toString(),
                        "planId", UUID.randomUUID().toString(),
                        "planSnapshot", Map.of("price", new BigDecimal("1.0"),
                                "currency", "BRL", "billingInterval", "MONTHLY")),
                UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void backCompatConsumeWithoutEventIdStillRoutes() {
        // The EventConsumer port has a 2-arg overload; ensure nothing blows up
        // when callers (older integrations) use it. A random eventId is generated.
        when(billingSubscriptionRepository.findBySubscriptionId(subscriptionId))
                .thenReturn(Optional.of(subscription));

        router.consume("subscription.cancel_requested",
                Map.of("subscriptionId", subscriptionId.toString()));

        verify(billingSubscriptionService).markCanceled(eq(subscription), any(Instant.class));
    }

    @Test
    void planCreatedUpsertsNewPlan() {
        UUID newPlanId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("planId", newPlanId.toString());
        payload.put("name", "Pro Plan");
        payload.put("price", new BigDecimal("29.90"));
        payload.put("currency", "BRL");
        payload.put("billingInterval", "MONTHLY");
        payload.put("trialDays", 14);
        payload.put("active", true);

        router.consume("plan.created", payload, UUID.randomUUID());

        ArgumentCaptor<BillingPlan> captor = ArgumentCaptor.forClass(BillingPlan.class);
        verify(planRepository).save(captor.capture());
        BillingPlan savedPlan = captor.getValue();
        assertThat(savedPlan.getPlanId()).isEqualTo(newPlanId);
        assertThat(savedPlan.getName()).isEqualTo("Pro Plan");
        assertThat(savedPlan.getPrice()).isEqualByComparingTo("29.90");
        assertThat(savedPlan.getCurrency()).isEqualTo("BRL");
        assertThat(savedPlan.getBillingInterval()).isEqualTo("MONTHLY");
        assertThat(savedPlan.getTrialDays()).isEqualTo(14);
        assertThat(savedPlan.getActive()).isTrue();
    }

    @Test
    void planUpdatedUpdatesExistingPlan() {
        UUID existingPlanId = UUID.randomUUID();
        BillingPlan existingPlan = BillingPlan.builder()
                .id(UUID.randomUUID())
                .planId(existingPlanId)
                .name("Basic Plan")
                .price(new BigDecimal("9.90"))
                .currency("USD")
                .billingInterval("MONTHLY")
                .trialDays(0)
                .active(true)
                .build();
        when(planRepository.findByPlanId(existingPlanId)).thenReturn(Optional.of(existingPlan));

        Map<String, Object> payload = new HashMap<>();
        payload.put("planId", existingPlanId.toString());
        payload.put("name", "Basic Plan Updated");
        payload.put("price", new BigDecimal("14.90"));
        payload.put("currency", "EUR");
        payload.put("billingInterval", "YEARLY");
        payload.put("trialDays", 30);
        payload.put("active", false);

        router.consume("plan.updated", payload, UUID.randomUUID());

        ArgumentCaptor<BillingPlan> captor = ArgumentCaptor.forClass(BillingPlan.class);
        verify(planRepository).save(captor.capture());
        BillingPlan updatedPlan = captor.getValue();
        assertThat(updatedPlan.getPlanId()).isEqualTo(existingPlanId);
        assertThat(updatedPlan.getName()).isEqualTo("Basic Plan Updated");
        assertThat(updatedPlan.getPrice()).isEqualByComparingTo("14.90");
        assertThat(updatedPlan.getCurrency()).isEqualTo("EUR");
        assertThat(updatedPlan.getBillingInterval()).isEqualTo("YEARLY");
        assertThat(updatedPlan.getTrialDays()).isEqualTo(30);
        assertThat(updatedPlan.getActive()).isFalse();
    }

    @Test
    void planDeletedRemovesPlan() {
        UUID planId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("planId", planId.toString());

        router.consume("plan.deleted", payload, UUID.randomUUID());

        verify(planRepository).deleteByPlanId(planId);
    }

    @Test
    void planCreatedWithMissingPlanIdThrows() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Pro Plan");
        payload.put("price", new BigDecimal("29.90"));
        payload.put("currency", "BRL");
        payload.put("billingInterval", "MONTHLY");
        payload.put("active", true);

        assertThatThrownBy(() -> router.consume("plan.created", payload, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class);
    }
}
