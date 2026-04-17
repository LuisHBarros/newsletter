package com.assine.billing.application.customer;

import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.repository.BillingCustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves or provisions a {@link BillingCustomer} for a given userId.
 * TODO: integrate with a real provider (Stripe Customer) instead of marking as FAKE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingCustomerService {

    private final BillingCustomerRepository customerRepository;

    @Transactional
    public BillingCustomer findOrCreate(UUID userId) {
        return customerRepository.findByUserId(userId)
            .orElseGet(() -> {
                BillingCustomer created = BillingCustomer.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .provider("FAKE")
                    .providerCustomerRef("fake_cus_" + UUID.randomUUID())
                    .build();
                BillingCustomer saved = customerRepository.save(created);
                log.info("Provisioned billing customer: id={} userId={}", saved.getId(), userId);
                return saved;
            });
    }
}
