package com.assine.billing.adapters.inbound.rest.payment.dto;

import com.assine.billing.domain.payment.model.PaymentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record PaymentFilter(
    UUID customerId,
    UUID subscriptionId,
    PaymentStatus status,
    LocalDate startDate,
    LocalDate endDate
) {}
