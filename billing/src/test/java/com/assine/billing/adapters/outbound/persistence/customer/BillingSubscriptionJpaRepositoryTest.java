package com.assine.billing.adapters.outbound.persistence.customer;

import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.model.BillingSubscription;
import com.assine.billing.domain.customer.model.BillingSubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BillingSubscriptionJpaRepositoryTest {

    @Autowired private BillingSubscriptionJpaRepository repository;
    @Autowired private BillingCustomerJpaRepository customerRepository;

    private BillingCustomer customer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        customerRepository.deleteAll();
        customer = customerRepository.save(BillingCustomer.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .provider("STRIPE")
                .build());
    }

    @Test
    void shouldFindBySubscriptionId() {
        UUID subId = UUID.randomUUID();
        repository.save(BillingSubscription.builder()
                .id(UUID.randomUUID())
                .subscriptionId(subId)
                .customer(customer)
                .planId(UUID.randomUUID())
                .status(BillingSubscriptionStatus.PENDING)
                .billingInterval("monthly")
                .build());

        Optional<BillingSubscription> found = repository.findBySubscriptionId(subId);
        assertThat(found).isPresent();
        assertThat(found.get().getSubscriptionId()).isEqualTo(subId);
        assertThat(repository.findBySubscriptionId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldPersistStatusTransitions() {
        BillingSubscription saved = repository.save(BillingSubscription.builder()
                .id(UUID.randomUUID())
                .subscriptionId(UUID.randomUUID())
                .customer(customer)
                .planId(UUID.randomUUID())
                .status(BillingSubscriptionStatus.PENDING)
                .billingInterval("monthly")
                .build());

        saved.setStatus(BillingSubscriptionStatus.ACTIVE);
        saved.setCurrentPeriodStart(Instant.parse("2026-04-01T00:00:00Z"));
        saved.setCurrentPeriodEnd(Instant.parse("2026-05-01T00:00:00Z"));
        repository.saveAndFlush(saved);

        BillingSubscription reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BillingSubscriptionStatus.ACTIVE);
        assertThat(reloaded.getCurrentPeriodStart()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));

        reloaded.setStatus(BillingSubscriptionStatus.CANCELED);
        reloaded.setCanceledAt(Instant.parse("2026-04-20T00:00:00Z"));
        repository.saveAndFlush(reloaded);

        BillingSubscription canceled = repository.findById(saved.getId()).orElseThrow();
        assertThat(canceled.getStatus()).isEqualTo(BillingSubscriptionStatus.CANCELED);
        assertThat(canceled.getCanceledAt()).isEqualTo(Instant.parse("2026-04-20T00:00:00Z"));
    }

    @Test
    void shouldEnforceUniqueSubscriptionId() {
        UUID subId = UUID.randomUUID();
        repository.saveAndFlush(BillingSubscription.builder()
                .id(UUID.randomUUID())
                .subscriptionId(subId)
                .customer(customer)
                .planId(UUID.randomUUID())
                .status(BillingSubscriptionStatus.PENDING)
                .billingInterval("monthly")
                .build());

        BillingSubscription dup = BillingSubscription.builder()
                .id(UUID.randomUUID())
                .subscriptionId(subId)
                .customer(customer)
                .planId(UUID.randomUUID())
                .status(BillingSubscriptionStatus.PENDING)
                .billingInterval("monthly")
                .build();
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(dup));
    }
}
