package com.assine.subscriptions.contracts;

import com.assine.subscriptions.application.outbox.OutboxEventService;
import com.assine.subscriptions.application.plan.PlanService;
import com.assine.subscriptions.application.subscription.SubscriptionService;
import com.assine.subscriptions.domain.plan.model.BillingInterval;
import com.assine.subscriptions.domain.plan.model.Plan;
import com.assine.subscriptions.domain.plan.repository.PlanRepository;
import com.assine.subscriptions.domain.subscription.model.Subscription;
import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;
import com.assine.subscriptions.domain.subscription.repository.SubscriptionRepository;
import com.assine.subscriptions.domain.subscription.service.SubscriptionStateGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validates that published event payloads conform to the JSON Schemas under
 * src/main/resources/contracts/published.
 */
@ExtendWith(MockitoExtension.class)
class PublishedEventContractTest {

    private static final JsonSchemaFactory FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private OutboxEventService outboxEventService;

    private SubscriptionService subscriptionService;
    private PlanService planService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                subscriptionRepository, planRepository, outboxEventService, new SubscriptionStateGuard()
        );
        planService = new PlanService(planRepository, outboxEventService);
    }

    @Test
    void subscriptionRequestedPayloadMatchesSchema() {
        UUID userId = UUID.randomUUID();
        Plan plan = samplePlan();

        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(subscriptionRepository.existsByUserIdAndPlanId(userId, plan.getId())).thenReturn(false);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        subscriptionService.createSubscription(userId, plan.getId(), Map.of());

        Map<String, Object> payload = capturePayload("subscription.requested");
        assertValid("subscription.requested.v1.json", payload);
    }

    @Test
    void subscriptionActivatedPayloadMatchesSchema() {
        Subscription subscription = sampleSubscription(SubscriptionStatus.PENDING_PAYMENT);
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        subscriptionService.activate(subscription.getId(),
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"));

        Map<String, Object> payload = capturePayload("subscription.activated");
        assertValid("subscription.activated.v1.json", payload);
    }

    @Test
    void subscriptionPastDuePayloadMatchesSchema() {
        Subscription subscription = sampleSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        subscriptionService.markPastDue(subscription.getId());

        Map<String, Object> payload = capturePayload("subscription.past_due");
        assertValid("subscription.past_due.v1.json", payload);
    }

    @Test
    void subscriptionCanceledPayloadMatchesSchema() {
        Subscription subscription = sampleSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        subscriptionService.confirmCanceled(subscription.getId(),
                Instant.parse("2026-05-10T00:00:00Z"), "user_requested");

        Map<String, Object> payload = capturePayload("subscription.canceled");
        assertValid("subscription.canceled.v1.json", payload);
    }

    @Test
    void subscriptionCancelRequestedPayloadMatchesSchema() {
        Subscription subscription = sampleSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        subscriptionService.cancelSubscription(subscription.getId(), true, null);

        Map<String, Object> payload = capturePayload("subscription.cancel_requested");
        assertValid("subscription.cancel_requested.v1.json", payload);
    }

    @Test
    void subscriptionPeriodRenewedPayloadMatchesSchema() {
        Subscription subscription = sampleSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        subscriptionService.renewPeriod(subscription.getId(),
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"));

        Map<String, Object> payload = capturePayload("subscription.period_renewed");
        assertValid("subscription.period_renewed.v1.json", payload);
    }

    @Test
    void subscriptionExpiredPayloadMatchesSchema() {
        Subscription subscription = sampleSubscription(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodEnd(Instant.parse("2026-03-01T00:00:00Z"));
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        subscriptionService.expire(subscription.getId());

        Map<String, Object> payload = capturePayload("subscription.expired");
        assertValid("subscription.expired.v1.json", payload);
    }

    @Test
    void planCreatedPayloadMatchesSchema() {
        when(planRepository.save(any(Plan.class))).thenAnswer(i -> i.getArgument(0));

        planService.createPlan("Gold", "Gold plan", new BigDecimal("49.90"), "BRL",
                BillingInterval.MONTHLY, 7, Map.of());

        Map<String, Object> payload = capturePayload("plan.created");
        assertValid("plan.created.v1.json", payload);
    }

    @Test
    void planUpdatedPayloadMatchesSchema() {
        Plan plan = samplePlan();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(planRepository.save(any(Plan.class))).thenAnswer(i -> i.getArgument(0));

        planService.updatePlan(plan.getId(), "Updated", null, new BigDecimal("59.90"), "BRL",
                BillingInterval.YEARLY, 14, Map.of(), true);

        Map<String, Object> payload = capturePayload("plan.updated");
        assertValid("plan.updated.v1.json", payload);
    }

    @Test
    void planDeletedPayloadMatchesSchema() {
        Plan plan = samplePlan();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));

        planService.deletePlan(plan.getId());

        Map<String, Object> payload = capturePayload("plan.deleted");
        assertValid("plan.deleted.v1.json", payload);
    }

    private Map<String, Object> capturePayload(String eventType) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).createEvent(eq(eventType), any(), any(), payloadCaptor.capture());
        return payloadCaptor.getValue();
    }

    private void assertValid(String schemaFileName, Map<String, Object> payload) {
        JsonSchema schema = loadSchema("contracts/published/" + schemaFileName);
        JsonNode node = MAPPER.valueToTree(payload);
        Set<ValidationMessage> errors = schema.validate(node);
        assertThat(errors)
                .as("Schema validation errors for %s: payload=%s", schemaFileName, payload)
                .isEmpty();
    }

    private JsonSchema loadSchema(String resourcePath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Schema not found: " + resourcePath);
            }
            return FACTORY.getSchema(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load schema " + resourcePath, e);
        }
    }

    private Plan samplePlan() {
        return Plan.builder()
                .id(UUID.randomUUID())
                .name("Gold")
                .description("Gold plan")
                .price(new BigDecimal("29.90"))
                .currency("BRL")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(0)
                .features(Map.of())
                .active(true)
                .build();
    }

    private Subscription sampleSubscription(SubscriptionStatus status) {
        Plan plan = samplePlan();
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
