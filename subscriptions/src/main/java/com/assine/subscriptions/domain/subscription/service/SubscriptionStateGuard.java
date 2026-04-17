package com.assine.subscriptions.domain.subscription.service;

import com.assine.subscriptions.domain.subscription.exception.IllegalStateTransitionException;
import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Service-level guard for subscription state transitions.
 * Defines valid transitions statically based on the FSM in events.md.
 * Terminal states: CANCELED, EXPIRED
 */
@Slf4j
@Component
public class SubscriptionStateGuard {

    // Define valid transitions as per FSM documentation
    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> VALID_TRANSITIONS = Map.of(
        SubscriptionStatus.PENDING_PAYMENT, EnumSet.of(
            SubscriptionStatus.TRIAL,
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.CANCELED
        ),
        SubscriptionStatus.TRIAL, EnumSet.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.CANCELED,
            SubscriptionStatus.EXPIRED
        ),
        SubscriptionStatus.ACTIVE, EnumSet.of(
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.CANCELED,
            SubscriptionStatus.EXPIRED
        ),
        SubscriptionStatus.PAST_DUE, EnumSet.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.CANCELED,
            SubscriptionStatus.EXPIRED
        ),
        // Terminal states have no valid transitions
        SubscriptionStatus.CANCELED, EnumSet.noneOf(SubscriptionStatus.class),
        SubscriptionStatus.EXPIRED, EnumSet.noneOf(SubscriptionStatus.class),
        SubscriptionStatus.INCOMPLETE, EnumSet.noneOf(SubscriptionStatus.class)
    );

    /**
     * Validates if a transition from current status to target status is allowed.
     * Throws IllegalStateTransitionException if the transition is invalid.
     *
     * @param currentStatus the current status of the subscription
     * @param targetStatus the desired target status
     * @throws IllegalStateTransitionException if the transition is not permitted
     */
    public void assertTransition(SubscriptionStatus currentStatus, SubscriptionStatus targetStatus) {
        if (currentStatus == targetStatus) {
            // Same state is always valid (idempotent)
            return;
        }

        Set<SubscriptionStatus> validTargets = VALID_TRANSITIONS.get(currentStatus);
        if (validTargets == null || !validTargets.contains(targetStatus)) {
            throw new IllegalStateTransitionException(currentStatus, targetStatus);
        }

        log.debug("Validated transition: {} -> {}", currentStatus, targetStatus);
    }

    /**
     * Determines if a transition is valid without throwing.
     *
     * @param currentStatus current status
     * @param targetStatus target status
     * @return true if the transition is valid
     */
    public boolean isValidTransition(SubscriptionStatus currentStatus, SubscriptionStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return true;
        }

        Set<SubscriptionStatus> validTargets = VALID_TRANSITIONS.get(currentStatus);
        return validTargets != null && validTargets.contains(targetStatus);
    }
}
