package com.assine.subscriptions.adapters.inbound.rest.subscription.dto;

public record CancelSubscriptionRequest(
    boolean cancelAtPeriodEnd
) {}
