package com.assine.billing.adapters.outbound.persistence.customer;

import com.assine.billing.domain.customer.model.BillingCustomer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BillingCustomerJpaRepositoryTest {

    @Autowired private BillingCustomerJpaRepository repository;

    @BeforeEach
    void reset() {
        repository.deleteAll();
    }

    @Test
    void shouldPersistProviderCustomerRef() {
        UUID userId = UUID.randomUUID();
        BillingCustomer saved = repository.save(BillingCustomer.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .provider("STRIPE")
                .providerCustomerRef("cus_abc")
                .email("user@example.com")
                .build());

        BillingCustomer fetched = repository.findById(saved.getId()).orElseThrow();
        assertThat(fetched.getProvider()).isEqualTo("STRIPE");
        assertThat(fetched.getProviderCustomerRef()).isEqualTo("cus_abc");
        assertThat(fetched.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void shouldFindByUserId() {
        UUID userId = UUID.randomUUID();
        repository.save(BillingCustomer.builder()
                .id(UUID.randomUUID()).userId(userId).provider("STRIPE").build());

        Optional<BillingCustomer> found = repository.findByUserId(userId);
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);

        assertThat(repository.findByUserId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldEnforceUniqueUserId() {
        UUID userId = UUID.randomUUID();
        repository.saveAndFlush(BillingCustomer.builder()
                .id(UUID.randomUUID()).userId(userId).provider("STRIPE").build());

        BillingCustomer dup = BillingCustomer.builder()
                .id(UUID.randomUUID()).userId(userId).provider("FAKE").build();
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(dup));
    }
}
