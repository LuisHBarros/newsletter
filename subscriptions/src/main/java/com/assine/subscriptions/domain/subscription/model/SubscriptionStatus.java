package com.assine.subscriptions.domain.subscription.model;

public enum SubscriptionStatus {
    PENDING_PAYMENT,
    TRIAL,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    INCOMPLETE,
    EXPIRED
}
