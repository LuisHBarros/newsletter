package com.assine.billing.application.payment;

import com.assine.billing.application.customer.BillingSubscriptionService;
import com.assine.billing.application.outbox.OutboxEventService;
import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.model.BillingSubscription;
import com.assine.billing.domain.customer.model.BillingSubscriptionStatus;
import com.assine.billing.domain.payment.model.Payment;
import com.assine.billing.domain.payment.model.PaymentStatus;
import com.assine.billing.domain.payment.repository.PaymentRepository;
import com.assine.billing.domain.outbox.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentStatusUpdateService paymentStatusUpdateService;
    @Mock private BillingSubscriptionService billingSubscriptionService;
    @Mock private OutboxEventService outboxEventService;
    @Mock private ProcessedEventRepository processedEventRepository;

    private StripeWebhookService service;

    private BillingCustomer customer;
    private BillingSubscription subscription;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new StripeWebhookService(paymentRepository, paymentStatusUpdateService,
                billingSubscriptionService, outboxEventService, processedEventRepository);

        customer = BillingCustomer.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .provider("STRIPE")
                .build();
        subscription = BillingSubscription.builder()
                .id(UUID.randomUUID())
                .subscriptionId(UUID.randomUUID())
                .customer(customer)
                .planId(UUID.randomUUID())
                .status(BillingSubscriptionStatus.PENDING)
                .providerSubscriptionRef("sub_ref")
                .currentPeriodStart(Instant.parse("2026-04-01T00:00:00Z"))
                .currentPeriodEnd(Instant.parse("2026-05-01T00:00:00Z"))
                .billingInterval("MONTHLY")
                .build();
        payment = Payment.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .subscription(subscription)
                .amount(new BigDecimal("29.90"))
                .currency("BRL")
                .status(PaymentStatus.PENDING)
                .provider("STRIPE")
                .providerPaymentRef("pi_123")
                .build();
    }

    @Test
    void succeededMarksPaymentAndActivatesSubscription() {
        when(paymentRepository.findByProviderPaymentRef("pi_123")).thenReturn(Optional.of(payment));
        org.mockito.Mockito.doAnswer(invocation -> {
            BillingSubscription sub = invocation.getArgument(0);
            sub.setCurrentPeriodStart(Instant.now());
            sub.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
            return null;
        }).when(billingSubscriptionService).markActive(subscription);

        service.handlePaymentSucceeded("pi_123", null);

        verify(paymentStatusUpdateService).execute(payment.getId(), PaymentStatus.SUCCEEDED, null);
        verify(billingSubscriptionService).markActive(subscription);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).createEvent(eq("billing.payment.succeeded"), eq("Payment"),
                eq(payment.getId()), captor.capture());
        assertThat(captor.getValue())
                .containsEntry("paymentId", payment.getId().toString())
                .containsEntry("providerPaymentRef", "pi_123")
                .containsEntry("subscriptionId", subscription.getSubscriptionId().toString())
                .containsEntry("currency", "BRL");

        verify(outboxEventService).createEvent(eq("billing.subscription.activated"), eq("Subscription"),
                eq(subscription.getSubscriptionId()), any(Map.class));

        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("currentPeriodStart")).isNotNull();
        assertThat(payload.get("currentPeriodEnd")).isNotNull();
        assertThat(Instant.parse(payload.get("currentPeriodStart").toString())).isAfter(Instant.now().minusSeconds(60));
        assertThat(Instant.parse(payload.get("currentPeriodEnd").toString())).isAfter(Instant.now());
    }

    @Test
    void succeededIsIdempotentWhenPaymentAlreadySucceeded() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByProviderPaymentRef("pi_123")).thenReturn(Optional.of(payment));

        service.handlePaymentSucceeded("pi_123", null);

        verifyNoInteractions(paymentStatusUpdateService);
        verifyNoInteractions(outboxEventService);
        verify(billingSubscriptionService, never()).markActive(any());
    }

    @Test
    void succeededWithoutSubscriptionSkipsActivation() {
        payment.setSubscription(null);
        when(paymentRepository.findByProviderPaymentRef("pi_123")).thenReturn(Optional.of(payment));

        service.handlePaymentSucceeded("pi_123", null);

        verify(paymentStatusUpdateService).execute(payment.getId(), PaymentStatus.SUCCEEDED, null);
        verify(outboxEventService).createEvent(eq("billing.payment.succeeded"), any(), any(), any());
        verify(outboxEventService, never()).createEvent(eq("billing.subscription.activated"), any(), any(), any());
        verify(billingSubscriptionService, never()).markActive(any());
    }

    @Test
    void succeededFallsBackToMetadataPaymentIdWhenRefNotFound() {
        when(paymentRepository.findByProviderPaymentRef("pi_nomatch")).thenReturn(Optional.empty());
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        service.handlePaymentSucceeded("pi_nomatch", payment.getId().toString());

        verify(paymentStatusUpdateService).execute(payment.getId(), PaymentStatus.SUCCEEDED, null);
    }

    @Test
    void succeededIsNoopWhenPaymentNotFoundAnywhere() {
        when(paymentRepository.findByProviderPaymentRef("pi_nomatch")).thenReturn(Optional.empty());

        service.handlePaymentSucceeded("pi_nomatch", null);

        verifyNoInteractions(paymentStatusUpdateService);
        verifyNoInteractions(outboxEventService);
        verifyNoInteractions(billingSubscriptionService);
    }

    @Test
    void failedMarksPaymentAndEmitsFailedEvent() {
        when(paymentRepository.findByProviderPaymentRef("pi_123")).thenReturn(Optional.of(payment));

        service.handlePaymentFailed("pi_123", null, "card_declined");

        verify(paymentStatusUpdateService).execute(payment.getId(), PaymentStatus.FAILED, "card_declined");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).createEvent(eq("billing.payment.failed"), eq("Payment"),
                eq(payment.getId()), captor.capture());
        assertThat(captor.getValue())
                .containsEntry("reason", "card_declined")
                .containsEntry("subscriptionId", subscription.getSubscriptionId().toString())
                .containsEntry("attempts", 1);

        verify(billingSubscriptionService, never()).markActive(any());
        verify(outboxEventService, never()).createEvent(eq("billing.payment.succeeded"), any(), any(), any());
    }

    @Test
    void failedIsIdempotentWhenAlreadyFailed() {
        payment.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findByProviderPaymentRef("pi_123")).thenReturn(Optional.of(payment));

        service.handlePaymentFailed("pi_123", null, "any");

        verifyNoInteractions(paymentStatusUpdateService);
        verifyNoInteractions(outboxEventService);
    }

    @Test
    void refundedMarksPaymentRefunded() {
        when(paymentRepository.findByProviderPaymentRef("pi_123")).thenReturn(Optional.of(payment));

        service.handleChargeRefunded("pi_123", null);

        verify(paymentStatusUpdateService).execute(payment.getId(), PaymentStatus.REFUNDED, null);
    }
}
