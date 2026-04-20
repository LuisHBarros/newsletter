package com.assine.billing.adapters.outbound.persistence.payment;

import com.assine.billing.adapters.outbound.persistence.customer.BillingCustomerJpaRepository;
import com.assine.billing.adapters.outbound.persistence.customer.BillingSubscriptionJpaRepository;
import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.model.BillingSubscription;
import com.assine.billing.domain.customer.model.BillingSubscriptionStatus;
import com.assine.billing.domain.payment.model.Payment;
import com.assine.billing.domain.payment.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PaymentJpaRepositoryTest {

    @Autowired private PaymentJpaRepository repository;
    @Autowired private BillingCustomerJpaRepository customerRepository;
    @Autowired private BillingSubscriptionJpaRepository subscriptionRepository;

    private BillingCustomer customer;
    private BillingSubscription subscription;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        subscriptionRepository.deleteAll();
        customerRepository.deleteAll();

        customer = customerRepository.save(BillingCustomer.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .provider("STRIPE")
                .providerCustomerRef("cus_test")
                .build());
        subscription = subscriptionRepository.save(BillingSubscription.builder()
                .id(UUID.randomUUID())
                .subscriptionId(UUID.randomUUID())
                .customer(customer)
                .planId(UUID.randomUUID())
                .status(BillingSubscriptionStatus.PENDING)
                .billingInterval("monthly")
                .providerSubscriptionRef("sub_test")
                .build());
    }

    private Payment.PaymentBuilder base() {
        return Payment.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .subscription(subscription)
                .amount(new BigDecimal("29.90"))
                .currency("BRL")
                .status(PaymentStatus.PENDING)
                .provider("STRIPE");
    }

    @Test
    void shouldSaveAndFindById() {
        Payment saved = repository.save(base().providerPaymentRef("pi_1").build());

        Optional<Payment> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getProviderPaymentRef()).isEqualTo("pi_1");
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void shouldFindByProviderPaymentRef() {
        repository.save(base().providerPaymentRef("pi_aaa").build());
        repository.save(base().providerPaymentRef("pi_bbb").build());

        Optional<Payment> found = repository.findByProviderPaymentRef("pi_aaa");
        assertThat(found).isPresent();
        assertThat(found.get().getProviderPaymentRef()).isEqualTo("pi_aaa");

        assertThat(repository.findByProviderPaymentRef("pi_missing")).isEmpty();
    }

    @Test
    void shouldFilterByCustomerViaSpecification() {
        BillingCustomer other = customerRepository.save(BillingCustomer.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .provider("STRIPE")
                .build());

        repository.save(base().providerPaymentRef("pi_mine").build());
        repository.save(base().customer(other).subscription(null).providerPaymentRef("pi_other").build());

        Specification<Payment> spec = (root, query, cb) ->
                cb.equal(root.get("customer").get("id"), customer.getId());

        var page = repository.findAll(spec, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getProviderPaymentRef()).isEqualTo("pi_mine");
    }

    @Test
    void shouldFilterByStatusViaSpecification() {
        repository.save(base().status(PaymentStatus.SUCCEEDED).providerPaymentRef("pi_ok").build());
        repository.save(base().status(PaymentStatus.FAILED).providerPaymentRef("pi_bad").build());
        repository.save(base().status(PaymentStatus.PENDING).providerPaymentRef("pi_wait").build());

        Specification<Payment> spec = (root, query, cb) ->
                cb.equal(root.get("status"), PaymentStatus.SUCCEEDED);

        var page = repository.findAll(spec, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }
}
