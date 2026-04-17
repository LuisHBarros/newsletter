package com.assine.billing.domain.customer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Local projection of a subscription owned by the subscriptions service.
 * Tracks the link to the payment provider (e.g. Stripe subscription id) and the
 * billing-side lifecycle. {@code subscriptionId} is the id as known in the subscriptions
 * service (used as correlation id in events).
 */
@Entity
@Table(name = "billing_subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingSubscription {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "subscription_id", nullable = false, unique = true)
    private UUID subscriptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private BillingCustomer customer;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private BillingSubscriptionStatus status;

    @Column(name = "provider_subscription_ref")
    private String providerSubscriptionRef;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "metadata", columnDefinition = "JSONB")
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version")
    @Builder.Default
    private Integer version = 0;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
