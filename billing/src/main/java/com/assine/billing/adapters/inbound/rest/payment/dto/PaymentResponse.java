package com.assine.billing.adapters.inbound.rest.payment.dto;

import com.assine.billing.domain.payment.model.Payment;
import com.assine.billing.domain.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID customerId,
    UUID subscriptionId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String provider,
    String providerPaymentRef,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
            p.getId(),
            p.getCustomer() != null ? p.getCustomer().getId() : null,
            p.getSubscription() != null ? p.getSubscription().getId() : null,
            p.getAmount(),
            p.getCurrency(),
            p.getStatus(),
            p.getProvider(),
            p.getProviderPaymentRef(),
            p.getFailureReason(),
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }
}
