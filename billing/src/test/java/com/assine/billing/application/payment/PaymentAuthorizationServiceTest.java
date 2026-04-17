package com.assine.billing.application.payment;

import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.repository.BillingCustomerRepository;
import com.assine.billing.domain.payment.exception.PaymentException;
import com.assine.billing.domain.payment.exception.UnauthorizedPaymentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAuthorizationServiceTest {

    @Mock private BillingCustomerRepository customerRepository;
    @InjectMocks private PaymentAuthorizationService service;

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> service.authorize(UUID.randomUUID(), BigDecimal.ZERO, "BRL"))
            .isInstanceOf(PaymentException.class)
            .hasMessageContaining("greater than zero");
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThatThrownBy(() -> service.authorize(UUID.randomUUID(), new BigDecimal("10"), "XXX"))
            .isInstanceOf(PaymentException.class)
            .hasMessageContaining("unsupported currency");
    }

    @Test
    void rejectsUnknownCustomer() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authorize(id, new BigDecimal("10"), "BRL"))
            .isInstanceOf(UnauthorizedPaymentException.class)
            .hasMessageContaining("customer not found");
    }

    @Test
    void returnsCustomerOnHappyPath() {
        UUID id = UUID.randomUUID();
        BillingCustomer customer = BillingCustomer.builder().id(id).userId(UUID.randomUUID()).build();
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));
        assertThat(service.authorize(id, new BigDecimal("10"), "brl")).isEqualTo(customer);
    }
}
