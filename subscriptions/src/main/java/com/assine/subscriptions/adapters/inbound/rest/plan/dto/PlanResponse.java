package com.assine.subscriptions.adapters.inbound.rest.plan.dto;

import com.assine.subscriptions.domain.plan.model.BillingInterval;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PlanResponse(
    UUID id,
    String name,
    String description,
    BigDecimal price,
    String currency,
    BillingInterval billingInterval,
    Integer trialDays,
    Map<String, Object> features,
    Boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
