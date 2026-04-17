package com.assine.billing.application.payment;

import com.assine.billing.domain.payment.exception.PaymentNotFoundException;
import com.assine.billing.domain.payment.model.Payment;
import com.assine.billing.domain.payment.model.PaymentStatus;
import com.assine.billing.domain.payment.repository.PaymentRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ported from {@code TransferStatusUpdateService}. Used by provider webhooks / event
 * handlers when an asynchronous status change arrives (e.g. Stripe payment_intent.succeeded).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentStatusUpdateService {

    private final PaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(@NonNull UUID paymentId, @NonNull PaymentStatus newStatus, String failureReason) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("payment not found: " + paymentId));

        if (payment.getStatus() == newStatus) {
            log.info("Payment status already {} for paymentId={}, skipping", newStatus, paymentId);
            return;
        }

        payment.setStatus(newStatus);
        if (newStatus == PaymentStatus.FAILED && failureReason != null) {
            payment.setFailureReason(failureReason);
        }
        paymentRepository.save(payment);
        log.info("Updated payment status to {} for paymentId={}", newStatus, paymentId);
    }
}
