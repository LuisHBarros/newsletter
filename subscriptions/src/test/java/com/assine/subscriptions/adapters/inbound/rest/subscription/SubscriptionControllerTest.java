package com.assine.subscriptions.adapters.inbound.rest.subscription;

import com.assine.subscriptions.adapters.inbound.rest.subscription.dto.CancelSubscriptionRequest;
import com.assine.subscriptions.adapters.inbound.rest.subscription.dto.CreateSubscriptionRequest;
import com.assine.subscriptions.adapters.inbound.rest.subscription.dto.UpdateSubscriptionRequest;
import com.assine.subscriptions.application.subscription.SubscriptionService;
import com.assine.subscriptions.domain.plan.model.BillingInterval;
import com.assine.subscriptions.domain.plan.model.Plan;
import com.assine.subscriptions.domain.subscription.model.Subscription;
import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SubscriptionService subscriptionService;

    private UUID testId = UUID.randomUUID();
    private UUID userId = UUID.randomUUID();
    private UUID planId = UUID.randomUUID();

    @Test
    void shouldCreateSubscription() throws Exception {
        Plan plan = Plan.builder()
                .id(planId)
                .name("Premium Plan")
                .description("Premium plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of())
                .active(true)
                .build();

        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.PENDING_PAYMENT)
                .metadata(Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(subscriptionService.createSubscription(any(), any(), any()))
                .thenReturn(subscription);

        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                userId,
                planId,
                Map.of()
        );

        mockMvc.perform(post("/api/v1/subscriptions")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("scope", java.util.List.of("subscriptions:write"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(testId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void shouldGetSubscription() throws Exception {
        Plan plan = Plan.builder()
                .id(planId)
                .name("Premium Plan")
                .description("Premium plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of())
                .active(true)
                .build();

        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(Instant.now())
                .currentPeriodEnd(Instant.now().plusSeconds(2592000))
                .metadata(Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(subscriptionService.getSubscription(testId)).thenReturn(subscription);

        mockMvc.perform(get("/api/v1/subscriptions/" + testId)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void shouldGetSubscriptionsByUserId() throws Exception {
        Plan plan = Plan.builder()
                .id(planId)
                .name("Premium Plan")
                .description("Premium plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of())
                .active(true)
                .build();

        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .metadata(Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(subscriptionService.getUserSubscriptions(eq(userId), any())).thenReturn(new PageImpl<>(java.util.List.of(subscription), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/subscriptions")
                        .param("userId", userId.toString())
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldGetSubscriptionsByPlanId() throws Exception {
        Plan plan = Plan.builder()
                .id(planId)
                .name("Premium Plan")
                .description("Premium plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of())
                .active(true)
                .build();

        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .metadata(Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(subscriptionService.getPlanSubscriptions(eq(planId), any())).thenReturn(new PageImpl<>(java.util.List.of(subscription), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/subscriptions")
                        .param("planId", planId.toString())
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("scope", java.util.List.of("subscriptions:admin")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].planId").value(planId.toString()));
    }

    @Test
    void shouldGetSubscriptionsByStatus() throws Exception {
        Plan plan = Plan.builder()
                .id(planId)
                .name("Premium Plan")
                .description("Premium plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of())
                .active(true)
                .build();

        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .metadata(Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(subscriptionService.getSubscriptionsByStatus(eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(subscription), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/subscriptions")
                        .param("status", "ACTIVE")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("scope", java.util.List.of("subscriptions:admin")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
    }

    @Test
    void shouldUpdateSubscription() throws Exception {
        Plan plan = Plan.builder()
                .id(planId)
                .name("Premium Plan")
                .description("Premium plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of())
                .active(true)
                .build();

        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.PAST_DUE)
                .currentPeriodStart(Instant.now())
                .currentPeriodEnd(Instant.now().plusSeconds(2592000))
                .cancelAtPeriodEnd(true)
                .canceledAt(Instant.now())
                .metadata(Map.of("key", "value"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(subscriptionService.getSubscription(testId)).thenReturn(subscription);
        when(subscriptionService.updateSubscription(eq(testId), any(), any(), any()))
                .thenReturn(subscription);

        UpdateSubscriptionRequest request = new UpdateSubscriptionRequest(
                Instant.now().plusSeconds(2592000),
                true,
                Map.of("key", "value")
        );

        mockMvc.perform(put("/api/v1/subscriptions/" + testId)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("scope", java.util.List.of("subscriptions:write"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true));
    }

    @Test
    void shouldIgnoreForbiddenFieldsOnUpdate() throws Exception {
        // Regression: PUT must not mutate status, currentPeriodStart or canceledAt.
        // Those fields are owned by the internal event handlers in EventRouter.
        Plan plan = Plan.builder()
                .id(planId)
                .name("Premium Plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of())
                .active(true)
                .build();

        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .metadata(Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(subscriptionService.getSubscription(testId)).thenReturn(subscription);
        when(subscriptionService.updateSubscription(eq(testId), any(), any(), any()))
                .thenReturn(subscription);

        // Body deliberately includes forbidden fields; Jackson must drop them
        // (spring.jackson defaults ignore unknown properties) and the service
        // is called with the trimmed 4-arg signature only.
        String body = "{" +
                "\"currentPeriodStart\":\"2020-01-01T00:00:00Z\"," +
                "\"currentPeriodEnd\":\"2030-01-01T00:00:00Z\"," +
                "\"cancelAtPeriodEnd\":true," +
                "\"canceledAt\":\"2020-01-01T00:00:00Z\"," +
                "\"status\":\"CANCELED\"," +
                "\"metadata\":{}" +
                "}";

        mockMvc.perform(put("/api/v1/subscriptions/" + testId)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("scope", java.util.List.of("subscriptions:write"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(subscriptionService)
                .updateSubscription(eq(testId), any(), eq(true), any());
        // And the old 7-arg overload (which accepted canceledAt/currentPeriodStart)
        // no longer exists — verified at compile time by this test compiling.
    }

    @Test
    void shouldCancelSubscription() throws Exception {
        Plan plan = Plan.builder()
                .id(planId)
                .name("Premium Plan")
                .description("Premium plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of())
                .active(true)
                .build();

        // Cancellation is now an intent: status stays ACTIVE (or current),
        // only cancelAtPeriodEnd flips. CANCELED is set later by confirmCanceled
        // after billing.subscription.canceled event.
        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .cancelAtPeriodEnd(true)
                .metadata(Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(subscriptionService.getSubscription(testId)).thenReturn(subscription);

        CancelSubscriptionRequest request = new CancelSubscriptionRequest(true, "User requested cancellation");

        mockMvc.perform(post("/api/v1/subscriptions/" + testId + "/cancel")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("scope", java.util.List.of("subscriptions:write"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true));
    }

    @Test
    void shouldDeleteSubscriptionTriggersCancel() throws Exception {
        Plan plan = Plan.builder()
                .id(planId)
                .name("Premium Plan")
                .description("Premium plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of())
                .active(true)
                .build();

        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .cancelAtPeriodEnd(true)
                .metadata(Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(subscriptionService.getSubscription(testId)).thenReturn(subscription);

        mockMvc.perform(delete("/api/v1/subscriptions/" + testId)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("scope", java.util.List.of("subscriptions:write")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true));
    }

    @Test
    void shouldDeleteSubscriptionWithReason() throws Exception {
        Plan plan = Plan.builder()
                .id(planId)
                .name("Premium Plan")
                .description("Premium plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of())
                .active(true)
                .build();

        Subscription subscription = Subscription.builder()
                .id(testId)
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .cancelAtPeriodEnd(true)
                .metadata(Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(subscriptionService.getSubscription(testId)).thenReturn(subscription);

        mockMvc.perform(delete("/api/v1/subscriptions/" + testId)
                        .param("reason", "User wants to cancel")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("scope", java.util.List.of("subscriptions:write")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
