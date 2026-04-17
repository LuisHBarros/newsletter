package com.assine.subscriptions.adapters.inbound.rest.plan.dto;

import com.assine.subscriptions.domain.plan.model.BillingInterval;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record CreatePlanRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be less than 255 characters")
    String name,

    String description,

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be positive")
    BigDecimal price,

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    String currency,

    @NotNull(message = "Billing interval is required")
    BillingInterval billingInterval,

    Integer trialDays,

    Map<String, Object> features
) {}
