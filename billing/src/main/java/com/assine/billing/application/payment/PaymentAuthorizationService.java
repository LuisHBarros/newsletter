package com.assine.billing.application.payment;

import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.repository.BillingCustomerRepository;
import com.assine.billing.domain.payment.exception.PaymentException;
import com.assine.billing.domain.payment.exception.UnauthorizedPaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Ported from {@code TransferAuthorizationService}. In billing there are no wallets
 * nor sender/receiver roles — the only authorization is structural: customer exists,
 * amount positive, currency supported. Real provider-side authorization (e.g. Stripe
 * SetupIntent / PaymentMethod attachment) lives behind the {@link com.assine.billing.application.payment.provider.PaymentProviderPort}.
 */
@Service
@RequiredArgsConstructor
public class PaymentAuthorizationService {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("BRL", "USD", "EUR");

    private final BillingCustomerRepository customerRepository;

    public BillingCustomer authorize(UUID customerId, BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("amount must be greater than zero");
        }
        if (currency == null || !SUPPORTED_CURRENCIES.contains(currency.toUpperCase())) {
            throw new PaymentException("unsupported currency: " + currency);
        }
        return customerRepository.findById(customerId)
            .orElseThrow(() -> new UnauthorizedPaymentException("customer not found: " + customerId));
    }
}
