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
 * Persists email and propagates name to Stripe when creating/updating the customer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingCustomerService {

    private final BillingCustomerRepository customerRepository;

    @Transactional
    public BillingCustomer findOrCreate(UUID userId, String email, String name) {
        return customerRepository.findByUserId(userId)
            .map(existing -> {
                // Update email if provided and different/null
                if (email != null && !email.isBlank() && (existing.getEmail() == null || !existing.getEmail().equals(email))) {
                    existing.setEmail(email);
                    customerRepository.save(existing);
                    log.info("Updated billing customer email: id={} userId={}", existing.getId(), userId);
                }
                return existing;
            })
            .orElseGet(() -> {
                BillingCustomer created = BillingCustomer.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .email(email)
                    .provider("FAKE")
                    .providerCustomerRef("fake_cus_" + UUID.randomUUID())
                    .build();
                BillingCustomer saved = customerRepository.save(created);
                log.info("Provisioned billing customer: id={} userId={} email={}", saved.getId(), userId, email);
                return saved;
            });
    }

    /**
     * Legacy overload for backward compatibility (creates customer without email).
     */
    @Transactional
    public BillingCustomer findOrCreate(UUID userId) {
        return findOrCreate(userId, null, null);
    }
}
