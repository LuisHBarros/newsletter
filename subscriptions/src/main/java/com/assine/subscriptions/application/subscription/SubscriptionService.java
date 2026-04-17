package com.assine.subscriptions.application.subscription;

import com.assine.subscriptions.application.outbox.OutboxEventService;
import com.assine.subscriptions.domain.plan.model.Plan;
import com.assine.subscriptions.domain.plan.repository.PlanRepository;
import com.assine.subscriptions.domain.subscription.model.Subscription;
import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;
import com.assine.subscriptions.domain.subscription.repository.SubscriptionRepository;
import com.assine.subscriptions.domain.subscription.service.SubscriptionStateGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final OutboxEventService outboxEventService;
    private final SubscriptionStateGuard stateGuard;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
                              OutboxEventService outboxEventService, SubscriptionStateGuard stateGuard) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.outboxEventService = outboxEventService;
        this.stateGuard = stateGuard;
    }

    @Transactional
    public Subscription createSubscription(UUID userId, UUID planId, Map<String, Object> metadata) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found with id: " + planId));

        if (subscriptionRepository.existsByUserIdAndPlanId(userId, planId)) {
            throw new IllegalStateException("Subscription already exists for user and plan");
        }

        boolean hasTrial = plan.getTrialDays() != null && plan.getTrialDays() > 0;
        Instant now = Instant.now();
        Instant trialStart = hasTrial ? now : null;
        Instant trialEnd = hasTrial ? now.plus(Duration.ofDays(plan.getTrialDays())) : null;
        SubscriptionStatus initialStatus = hasTrial ? SubscriptionStatus.TRIAL : SubscriptionStatus.PENDING_PAYMENT;

        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .plan(plan)
                .status(initialStatus)
                .cancelAtPeriodEnd(false)
                .trialStart(trialStart)
                .trialEnd(trialEnd)
                .metadata(metadata != null ? metadata : Map.of())
                .build();

        Subscription savedSubscription = subscriptionRepository.save(subscription);

        // Publish intent event for billing to act on
        Map<String, Object> planSnapshot = new HashMap<>();
        planSnapshot.put("price", plan.getPrice());
        planSnapshot.put("currency", plan.getCurrency());
        planSnapshot.put("billingInterval", plan.getBillingInterval().toString());
        planSnapshot.put("trialDays", plan.getTrialDays());

        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", savedSubscription.getId().toString());
        payload.put("userId", savedSubscription.getUserId().toString());
        payload.put("planId", plan.getId().toString());
        payload.put("status", savedSubscription.getStatus().toString());
        payload.put("planSnapshot", planSnapshot);
        payload.put("metadata", savedSubscription.getMetadata());

        outboxEventService.createEvent(
                "subscription.requested",
                "Subscription",
                savedSubscription.getId(),
                payload
        );

        return savedSubscription;
    }

    /** Transition triggered by billing.subscription.activated. */
    @Transactional
    public Subscription activate(UUID subscriptionId, Instant currentPeriodStart, Instant currentPeriodEnd) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + subscriptionId));

        Instant periodStart = Objects.requireNonNull(currentPeriodStart, "currentPeriodStart is required");
        Instant periodEnd = Objects.requireNonNull(currentPeriodEnd, "currentPeriodEnd is required");

        stateGuard.assertTransition(subscription.getStatus(), SubscriptionStatus.ACTIVE);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(periodStart);
        subscription.setCurrentPeriodEnd(periodEnd);

        Subscription saved = subscriptionRepository.save(subscription);

        outboxEventService.createEvent(
                "subscription.activated",
                "Subscription",
                saved.getId(),
                Map.of(
                        "subscriptionId", saved.getId().toString(),
                        "userId", saved.getUserId().toString(),
                        "planId", saved.getPlan().getId().toString(),
                        "currentPeriodStart", periodStart.toString(),
                        "currentPeriodEnd", periodEnd.toString()
                )
        );
        return saved;
    }

    /** Transition triggered by billing.payment.failed after grace. */
    @Transactional
    public Subscription markPastDue(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + subscriptionId));

        stateGuard.assertTransition(subscription.getStatus(), SubscriptionStatus.PAST_DUE);
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        Subscription saved = subscriptionRepository.save(subscription);

        outboxEventService.createEvent(
                "subscription.past_due",
                "Subscription",
                saved.getId(),
                Map.of(
                        "subscriptionId", saved.getId().toString(),
                        "userId", saved.getUserId().toString()
                )
        );
        return saved;
    }

    /** Transition triggered by billing.subscription.canceled. */
    @Transactional
    public Subscription confirmCanceled(UUID subscriptionId, Instant canceledAt, String reason) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + subscriptionId));

        stateGuard.assertTransition(subscription.getStatus(), SubscriptionStatus.CANCELED);
        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscription.setCanceledAt(canceledAt != null ? canceledAt : Instant.now());
        Subscription saved = subscriptionRepository.save(subscription);

        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", saved.getId().toString());
        payload.put("userId", saved.getUserId().toString());
        payload.put("canceledAt", saved.getCanceledAt().toString());
        payload.put("reason", reason);

        outboxEventService.createEvent(
                "subscription.canceled",
                "Subscription",
                saved.getId(),
                payload
        );
        return saved;
    }

    /** Renew current period after successful billing payment. */
    @Transactional
    public Subscription renewPeriod(UUID subscriptionId, Instant newPeriodStart, Instant newPeriodEnd) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + subscriptionId));

        Instant periodStart = Objects.requireNonNull(newPeriodStart, "newPeriodStart is required");
        Instant periodEnd = Objects.requireNonNull(newPeriodEnd, "newPeriodEnd is required");

        // Renewal can bring back from PAST_DUE to ACTIVE or keep ACTIVE
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            stateGuard.assertTransition(subscription.getStatus(), SubscriptionStatus.ACTIVE);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
        }
        subscription.setCurrentPeriodStart(periodStart);
        subscription.setCurrentPeriodEnd(periodEnd);
        Subscription saved = subscriptionRepository.save(subscription);

        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", saved.getId().toString());
        payload.put("userId", saved.getUserId().toString());
        payload.put("planId", saved.getPlan().getId().toString());
        payload.put("currentPeriodStart", periodStart.toString());
        payload.put("currentPeriodEnd", periodEnd.toString());

        outboxEventService.createEvent(
                "subscription.period_renewed",
                "Subscription",
                saved.getId(),
                payload
        );
        return saved;
    }

    /**
     * Applies the (small) subset of mutations allowed via
     * {@code PUT /api/v1/subscriptions/{id}}. Lifecycle-critical fields
     * ({@code status}, {@code currentPeriodStart}, {@code canceledAt})
     * are intentionally absent from this signature: those are owned by the
     * billing event handlers in {@code EventRouter}
     * ({@link #activate}, {@link #renewPeriod}, {@link #confirmCanceled}).
     */
    @Transactional
    public Subscription updateSubscription(UUID id, Instant currentPeriodEnd,
                                           Boolean cancelAtPeriodEnd, Map<String, Object> metadata) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + id));

        if (currentPeriodEnd != null) subscription.setCurrentPeriodEnd(currentPeriodEnd);
        if (cancelAtPeriodEnd != null) subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
        if (metadata != null) subscription.setMetadata(metadata);

        Subscription savedSubscription = subscriptionRepository.save(subscription);

        // Publish event
        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", savedSubscription.getId().toString());
        payload.put("userId", savedSubscription.getUserId().toString());
        payload.put("planId", savedSubscription.getPlan().getId().toString());
        payload.put("status", savedSubscription.getStatus().toString());
        payload.put("cancelAtPeriodEnd", savedSubscription.getCancelAtPeriodEnd());
        outboxEventService.createEvent(
                "subscription.updated",
                "Subscription",
                savedSubscription.getId(),
                payload
        );

        return savedSubscription;
    }

    public Subscription getSubscription(UUID id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + id));
    }

    public Subscription getUserSubscription(UUID userId, UUID planId) {
        return subscriptionRepository.findByUserIdAndPlanId(userId, planId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found for user and plan"));
    }

    public List<Subscription> getUserSubscriptions(UUID userId) {
        return subscriptionRepository.findByUserId(userId);
    }

    public Page<Subscription> getUserSubscriptions(UUID userId, Pageable pageable) {
        return subscriptionRepository.findByUserId(userId, pageable);
    }

    public List<Subscription> getPlanSubscriptions(UUID planId) {
        return subscriptionRepository.findByPlanId(planId);
    }

    public Page<Subscription> getPlanSubscriptions(UUID planId, Pageable pageable) {
        return subscriptionRepository.findByPlanId(planId, pageable);
    }

    public List<Subscription> getSubscriptionsByStatus(SubscriptionStatus status) {
        return subscriptionRepository.findByStatus(status);
    }

    public Page<Subscription> getSubscriptionsByStatus(SubscriptionStatus status, Pageable pageable) {
        return subscriptionRepository.findByStatus(status, pageable);
    }

    public Page<Subscription> getAllSubscriptions(Pageable pageable) {
        return subscriptionRepository.findAll(pageable);
    }

    /**
     * User-initiated cancellation. This is strictly an <em>intent</em>:
     * the subscription stays in its current status and only flips the
     * {@code cancelAtPeriodEnd} flag. The actual transition to
     * {@link SubscriptionStatus#CANCELED} happens in
     * {@link #confirmCanceled(UUID, Instant, String)} when billing
     * publishes {@code billing.subscription.canceled}.
     */
    @Transactional
    public void cancelSubscription(UUID id, boolean cancelAtPeriodEnd, String reason) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + id));

        if (subscription.getStatus() == SubscriptionStatus.CANCELED
                || subscription.getStatus() == SubscriptionStatus.EXPIRED) {
            throw new IllegalStateException(
                    "Cannot cancel a subscription in terminal state: " + subscription.getStatus());
        }

        subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
        Subscription savedSubscription = subscriptionRepository.save(subscription);

        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", savedSubscription.getId().toString());
        payload.put("userId", savedSubscription.getUserId().toString());
        payload.put("planId", savedSubscription.getPlan().getId().toString());
        payload.put("cancelAtPeriodEnd", savedSubscription.getCancelAtPeriodEnd());
        if (reason != null && !reason.isBlank()) {
            payload.put("reason", reason);
        }
        outboxEventService.createEvent(
                "subscription.cancel_requested",
                "Subscription",
                savedSubscription.getId(),
                payload
        );
    }

    /** Transition to EXPIRED when current period ends without renewal. */
    @Transactional
    public Subscription expire(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + subscriptionId));

        stateGuard.assertTransition(subscription.getStatus(), SubscriptionStatus.EXPIRED);
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        Subscription saved = subscriptionRepository.save(subscription);

        outboxEventService.createEvent(
                "subscription.expired",
                "Subscription",
                saved.getId(),
                Map.of(
                        "subscriptionId", saved.getId().toString(),
                        "userId", saved.getUserId().toString(),
                        "planId", saved.getPlan().getId().toString(),
                        "expiredAt", Instant.now().toString()
                )
        );
        return saved;
    }

    /**
     * Expires all subscriptions whose current period has ended.
     * @return number of subscriptions expired
     */
    @Transactional
    public int expireDueSubscriptions() {
        Instant now = Instant.now();
        var expirable = subscriptionRepository.findExpirable(now);
        int count = 0;
        for (Subscription subscription : expirable) {
            try {
                expire(subscription.getId());
                count++;
            } catch (Exception e) {
                log.error("Failed to expire subscription {}: {}", subscription.getId(), e.getMessage());
            }
        }
        return count;
    }

    @Transactional
    public void deleteSubscription(UUID id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found with id: " + id));

        subscriptionRepository.delete(id);

        outboxEventService.createEvent(
                "subscription.deleted",
                "Subscription",
                subscription.getId(),
                Map.of(
                        "subscriptionId", subscription.getId().toString(),
                        "userId", subscription.getUserId().toString(),
                        "planId", subscription.getPlan().getId().toString()
                )
        );
    }
}
