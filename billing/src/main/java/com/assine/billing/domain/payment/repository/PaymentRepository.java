package com.assine.billing.domain.payment.repository;

import com.assine.billing.domain.payment.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(UUID id);
    Optional<Payment> findByProviderPaymentRef(String providerPaymentRef);
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);
}
