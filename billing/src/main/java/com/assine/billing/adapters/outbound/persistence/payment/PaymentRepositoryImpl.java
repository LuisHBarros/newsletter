package com.assine.billing.adapters.outbound.persistence.payment;

import com.assine.billing.domain.payment.model.Payment;
import com.assine.billing.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    @Override
    public Payment save(Payment payment) {
        return jpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByProviderPaymentRef(String providerPaymentRef) {
        return jpaRepository.findByProviderPaymentRef(providerPaymentRef);
    }

    @Override
    public Page<Payment> findAll(Specification<Payment> spec, Pageable pageable) {
        return jpaRepository.findAll(spec, pageable);
    }
}
