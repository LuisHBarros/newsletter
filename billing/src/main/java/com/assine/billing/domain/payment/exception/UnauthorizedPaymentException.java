package com.assine.billing.domain.payment.exception;

public class UnauthorizedPaymentException extends RuntimeException {
    public UnauthorizedPaymentException(String message) {
        super(message);
    }
}
