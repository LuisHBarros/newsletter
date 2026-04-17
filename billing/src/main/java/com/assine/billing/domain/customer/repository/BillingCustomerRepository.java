package com.assine.billing.domain.customer.repository;

import com.assine.billing.domain.customer.model.BillingCustomer;

import java.util.Optional;
import java.util.UUID;

public interface BillingCustomerRepository {
    BillingCustomer save(BillingCustomer customer);
    Optional<BillingCustomer> findById(UUID id);
    Optional<BillingCustomer> findByUserId(UUID userId);
}
