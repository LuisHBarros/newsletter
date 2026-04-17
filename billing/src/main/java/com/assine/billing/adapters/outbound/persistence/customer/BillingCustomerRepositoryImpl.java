package com.assine.billing.adapters.outbound.persistence.customer;

import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.repository.BillingCustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BillingCustomerRepositoryImpl implements BillingCustomerRepository {

    private final BillingCustomerJpaRepository jpaRepository;

    @Override
    public BillingCustomer save(BillingCustomer customer) {
        return jpaRepository.save(customer);
    }

    @Override
    public Optional<BillingCustomer> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<BillingCustomer> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId);
    }
}
