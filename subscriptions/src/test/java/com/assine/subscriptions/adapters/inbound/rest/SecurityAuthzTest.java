package com.assine.subscriptions.adapters.inbound.rest;

import com.assine.subscriptions.adapters.inbound.rest.plan.PlanController;
import com.assine.subscriptions.adapters.inbound.rest.plan.dto.CreatePlanRequest;
import com.assine.subscriptions.adapters.inbound.rest.subscription.SubscriptionController;
import com.assine.subscriptions.adapters.inbound.rest.subscription.dto.CreateSubscriptionRequest;
import com.assine.subscriptions.application.plan.PlanService;
import com.assine.subscriptions.application.subscription.SubscriptionService;
import com.assine.subscriptions.config.SecurityConfig;
import com.assine.subscriptions.domain.plan.model.BillingInterval;
import com.assine.subscriptions.domain.plan.model.Plan;
import com.assine.subscriptions.domain.subscription.model.Subscription;
import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({PlanController.class, SubscriptionController.class})
@Import(SecurityConfig.class)
class SecurityAuthzTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlanService planService;

    @MockBean
    private SubscriptionService subscriptionService;

    private UUID planId = UUID.randomUUID();
    private UUID userId = UUID.randomUUID();

    @Test
    void adminScopeCanMutatePlans() throws Exception {
        Plan plan = Plan.builder()
                .id(planId)
                .name("Test Plan")
                .price(new BigDecimal("9.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(0)
                .features(Map.of())
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(planService.createPlan(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(plan);

        CreatePlanRequest request = new CreatePlanRequest(
                "Test Plan", null, new BigDecimal("9.99"), "USD",
                BillingInterval.MONTHLY, 0, Map.of()
        );

        mockMvc.perform(post("/api/v1/plans")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("system")
                                .claim("scope", java.util.List.of("subscriptions:admin"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void writeOnlyScopeCannotMutatePlans() throws Exception {
        CreatePlanRequest request = new CreatePlanRequest(
                "Test Plan", null, new BigDecimal("9.99"), "USD",
                BillingInterval.MONTHLY, 0, Map.of()
        );

        mockMvc.perform(post("/api/v1/plans")
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("scope", java.util.List.of("subscriptions:write"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 500) {
                        String msg = "Got 500";
                        if (result.getResolvedException() != null) {
                            msg += " — resolved: " + result.getResolvedException().getClass().getName()
                                    + ": " + result.getResolvedException().getMessage();
                        }
                        throw new AssertionError(msg);
                    }
                    if (status != 403) {
                        throw new AssertionError("Expected 403 but got " + status);
                    }
                });
    }

    @Test
    void unauthenticatedRequestReturnsDenied() throws Exception {
        mockMvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 401 && status != 403) {
                        throw new AssertionError("Expected 401 or 403 but got " + status);
                    }
                });
    }

    @Test
    void writeScopeCanCreateOwnSubscription() throws Exception {
        Plan plan = Plan.builder()
                .id(planId)
                .name("Test Plan")
                .price(new BigDecimal("9.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(0)
                .features(Map.of())
                .active(true)
                .build();

        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.PENDING_PAYMENT)
                .metadata(Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(subscriptionService.createSubscription(any(), any(), any(), any(), any()))
                .thenReturn(subscription);

        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                userId, "user@example.com", "Test User", planId, Map.of()
        );

        mockMvc.perform(post("/api/v1/subscriptions")
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("scope", java.util.List.of("subscriptions:write"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
