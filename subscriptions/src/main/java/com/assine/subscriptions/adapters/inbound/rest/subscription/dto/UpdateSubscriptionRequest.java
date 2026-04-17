package com.assine.subscriptions.adapters.inbound.rest.subscription.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Fields mutable via {@code PUT /api/v1/subscriptions/{id}}.
 *
 * <p>{@code currentPeriodStart} and {@code canceledAt} are intentionally
 * excluded: those fields represent the authoritative billing timeline and
 * are only updated by the internal event handlers in {@code EventRouter}
 * (via {@code activate}, {@code renewPeriod}, {@code confirmCanceled}).
 * Allowing REST clients to overwrite them would break the invariant that
 * lifecycle transitions are driven by billing events.
 */
public record UpdateSubscriptionRequest(
    Instant currentPeriodEnd,

    Boolean cancelAtPeriodEnd,

    Map<String, Object> metadata
) {}
