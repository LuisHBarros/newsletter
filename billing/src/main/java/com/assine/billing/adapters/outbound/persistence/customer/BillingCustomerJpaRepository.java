package com.assine.billing.adapters.outbound.persistence.customer;

import com.assine.billing.domain.customer.model.BillingCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingCustomerJpaRepository extends JpaRepository<BillingCustomer, UUID> {
    Optional<BillingCustomer> findByUserId(UUID userId);
}
