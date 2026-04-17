package com.assine.billing.application.payment;

import com.assine.billing.adapters.inbound.rest.payment.dto.PaymentFilter;
import com.assine.billing.domain.payment.exception.PaymentNotFoundException;
import com.assine.billing.domain.payment.model.Payment;
import com.assine.billing.domain.payment.repository.PaymentRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetPaymentService {

    private final PaymentRepository paymentRepository;

    public Payment execute(@NonNull UUID id) {
        return paymentRepository.findById(id)
            .orElseThrow(() -> new PaymentNotFoundException("payment not found: " + id));
    }

    public Page<Payment> find(PaymentFilter filter, Pageable pageable) {
        Specification<Payment> spec = PaymentSpecification.withFilters(filter);
        return paymentRepository.findAll(spec, pageable);
    }
}
