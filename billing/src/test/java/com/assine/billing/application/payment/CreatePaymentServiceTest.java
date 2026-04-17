package com.assine.billing.application.payment;

import com.assine.billing.application.outbox.OutboxEventService;
import com.assine.billing.application.payment.provider.PaymentProviderPort;
import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.customer.model.BillingSubscription;
import com.assine.billing.domain.customer.model.BillingSubscriptionStatus;
import com.assine.billing.domain.payment.exception.PaymentException;
import com.assine.billing.domain.payment.model.Payment;
import com.assine.billing.domain.payment.model.PaymentStatus;
import com.assine.billing.domain.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentAuthorizationService authorizationService;
    @Mock private PaymentProviderPort paymentProvider;
    @Mock private OutboxEventService outboxEventService;

    @InjectMocks private CreatePaymentService service;

    private BillingCustomer customer;
    private BillingSubscription subscription;

    @BeforeEach
    void setUp() {
        customer = BillingCustomer.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .provider("FAKE")
            .build();
        subscription = BillingSubscription.builder()
            .id(UUID.randomUUID())
            .subscriptionId(UUID.randomUUID())
            .customer(customer)
            .planId(UUID.randomUUID())
            .status(BillingSubscriptionStatus.PENDING)
            .currentPeriodStart(Instant.now())
            .currentPeriodEnd(Instant.now().plusSeconds(3600))
            .providerSubscriptionRef("fake_sub_ref")
            .build();

        when(authorizationService.authorize(any(), any(), anyString())).thenReturn(customer);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void keepsPaymentPendingAndDoesNotPublishOnHappyPath() {
        // Async flow: provider returns the PaymentIntent ref immediately but the
        // succeeded/activated events are emitted later by the webhook, not here.
        when(paymentProvider.charge(any(), any(), anyString(), anyString())).thenReturn("pi_123");

        UUID id = service.execute(customer.getId(), subscription, new BigDecimal("29.90"), "BRL", "key-1");

        assertThat(id).isNotNull();
        verify(outboxEventService, never()).createEvent(eq("billing.payment.succeeded"), any(), any(), any());
        verify(outboxEventService, never()).createEvent(eq("billing.subscription.activated"), any(), any(), any());
        verify(outboxEventService, never()).createEvent(eq("billing.payment.failed"), any(), any(), any());
    }

    @Test
    void publishesFailedEventAndPropagatesWhenProviderRejects() {
        when(paymentProvider.charge(any(), any(), anyString(), anyString()))
            .thenThrow(new RuntimeException("card_declined"));

        assertThatThrownBy(() -> service.execute(customer.getId(), subscription,
                new BigDecimal("29.90"), "BRL", "key-2"))
            .isInstanceOf(PaymentException.class);

        // Saved row must be FAILED with a recorded reason.
        ArgumentCaptor<Payment> savedCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeast(2)).save(savedCaptor.capture());
        Payment last = savedCaptor.getAllValues().get(savedCaptor.getAllValues().size() - 1);
        assertThat(last.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(last.getFailureReason()).isEqualTo("card_declined");

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).createEvent(eq("billing.payment.failed"), eq("Payment"), any(UUID.class), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().get("reason")).isEqualTo("card_declined");
        verify(outboxEventService, never()).createEvent(eq("billing.payment.succeeded"), any(), any(), any());
    }

    @Test
    void worksWithoutSubscriptionAndEmitsNoEventsOnHappyPath() {
        when(paymentProvider.charge(any(), any(), anyString(), anyString())).thenReturn("pi_standalone");

        UUID id = service.execute(customer.getId(), null, new BigDecimal("10.00"), "BRL", "key-3");

        assertThat(id).isNotNull();
        verify(outboxEventService, never()).createEvent(eq("billing.payment.succeeded"), any(), any(), any());
        verify(outboxEventService, never()).createEvent(eq("billing.subscription.activated"), any(), any(), any());
        verify(outboxEventService, never()).createEvent(eq("billing.payment.failed"), any(), any(), any());
    }

    @Test
    void savesPaymentAsPendingWithProviderRefOnHappyPath() {
        when(paymentProvider.charge(any(), any(), anyString(), anyString())).thenReturn("pi_ok");

        service.execute(customer.getId(), subscription, new BigDecimal("29.90"), "BRL", "key-4");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeast(2)).save(captor.capture());
        Payment last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        // Async contract: row stays PENDING with the provider reference attached.
        assertThat(last.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(last.getProviderPaymentRef()).isEqualTo("pi_ok");
    }
}
