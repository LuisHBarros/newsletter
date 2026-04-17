package com.assine.subscriptions.adapters.inbound.rest.subscription.dto;

import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SubscriptionResponse(
    UUID id,
    UUID userId,
    UUID planId,
    String planName,
    SubscriptionStatus status,
    Instant currentPeriodStart,
    Instant currentPeriodEnd,
    Boolean cancelAtPeriodEnd,
    Instant canceledAt,
    Instant trialStart,
    Instant trialEnd,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt
) {}
