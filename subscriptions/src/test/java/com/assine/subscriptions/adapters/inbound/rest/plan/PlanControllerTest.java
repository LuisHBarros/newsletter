package com.assine.subscriptions.adapters.inbound.rest.plan;

import com.assine.subscriptions.adapters.inbound.rest.plan.dto.CreatePlanRequest;
import com.assine.subscriptions.adapters.inbound.rest.plan.dto.UpdatePlanRequest;
import com.assine.subscriptions.application.plan.PlanService;
import com.assine.subscriptions.domain.plan.model.BillingInterval;
import com.assine.subscriptions.domain.plan.model.Plan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlanController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlanService planService;

    private UUID testId = UUID.randomUUID();

    @Test
    void shouldCreatePlan() throws Exception {
        Plan plan = Plan.builder()
                .id(testId)
                .name("Premium Plan")
                .description("Premium subscription plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of("feature1", "Feature 1"))
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(planService.createPlan(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(plan);

        CreatePlanRequest request = new CreatePlanRequest(
                "Premium Plan",
                "Premium subscription plan",
                new BigDecimal("29.99"),
                "USD",
                BillingInterval.MONTHLY,
                14,
                Map.of("feature1", "Feature 1")
        );

        mockMvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(testId.toString()))
                .andExpect(jsonPath("$.name").value("Premium Plan"))
                .andExpect(jsonPath("$.price").value(29.99));
    }

    @Test
    void shouldGetPlan() throws Exception {
        Plan plan = Plan.builder()
                .id(testId)
                .name("Premium Plan")
                .description("Premium subscription plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of("feature1", "Feature 1"))
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(planService.getPlan(testId)).thenReturn(plan);

        mockMvc.perform(get("/api/v1/plans/" + testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testId.toString()))
                .andExpect(jsonPath("$.name").value("Premium Plan"));
    }

    @Test
    void shouldGetAllPlans() throws Exception {
        Plan plan1 = Plan.builder()
                .id(UUID.randomUUID())
                .name("Basic Plan")
                .description("Basic plan")
                .price(new BigDecimal("9.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(0)
                .features(Map.of())
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Plan plan2 = Plan.builder()
                .id(UUID.randomUUID())
                .name("Premium Plan")
                .description("Premium plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of("feature1", "Feature 1"))
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(planService.getAllPlans(any())).thenReturn(new PageImpl<>(java.util.List.of(plan1, plan2), PageRequest.of(0, 20), 2));

        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].name").value("Basic Plan"))
                .andExpect(jsonPath("$.items[1].name").value("Premium Plan"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void shouldGetActivePlans() throws Exception {
        Plan plan = Plan.builder()
                .id(UUID.randomUUID())
                .name("Premium Plan")
                .description("Premium plan")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .billingInterval(BillingInterval.MONTHLY)
                .trialDays(14)
                .features(Map.of("feature1", "Feature 1"))
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(planService.getActivePlans(any())).thenReturn(new PageImpl<>(java.util.List.of(plan), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/plans").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].name").value("Premium Plan"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldUpdatePlan() throws Exception {
        Plan plan = Plan.builder()
                .id(testId)
                .name("Updated Plan")
                .description("Updated description")
                .price(new BigDecimal("39.99"))
                .currency("USD")
                .billingInterval(BillingInterval.YEARLY)
                .trialDays(30)
                .features(Map.of("feature1", "Feature 1", "feature2", "Feature 2"))
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(planService.updatePlan(eq(testId), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(plan);

        UpdatePlanRequest request = new UpdatePlanRequest(
                "Updated Plan",
                "Updated description",
                new BigDecimal("39.99"),
                "USD",
                BillingInterval.YEARLY,
                30,
                Map.of("feature1", "Feature 1", "feature2", "Feature 2"),
                true
        );

        mockMvc.perform(put("/api/v1/plans/" + testId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Plan"))
                .andExpect(jsonPath("$.price").value(39.99));
    }

    @Test
    void shouldDeletePlan() throws Exception {
        mockMvc.perform(delete("/api/v1/plans/" + testId))
                .andExpect(status().isNoContent());
    }
}
