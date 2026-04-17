package com.assine.subscriptions.domain.subscription.exception;

import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;

/**
 * Thrown when attempting an invalid state transition in the subscription lifecycle.
 * Maps to HTTP 409 Conflict via GlobalExceptionHandler.
 */
public class IllegalStateTransitionException extends RuntimeException {

    private final SubscriptionStatus from;
    private final SubscriptionStatus to;

    public IllegalStateTransitionException(SubscriptionStatus from, SubscriptionStatus to) {
        super(String.format("Invalid state transition from %s to %s", from, to));
        this.from = from;
        this.to = to;
    }

    public IllegalStateTransitionException(SubscriptionStatus from, SubscriptionStatus to, String reason) {
        super(String.format("Invalid state transition from %s to %s: %s", from, to, reason));
        this.from = from;
        this.to = to;
    }

    public SubscriptionStatus getFrom() {
        return from;
    }

    public SubscriptionStatus getTo() {
        return to;
    }
}
