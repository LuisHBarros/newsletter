package com.assine.billing.adapters.inbound.rest.payment;

import com.assine.billing.application.payment.GetPaymentService;
import com.assine.billing.domain.customer.model.BillingCustomer;
import com.assine.billing.domain.payment.exception.PaymentNotFoundException;
import com.assine.billing.domain.payment.model.Payment;
import com.assine.billing.domain.payment.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private GetPaymentService getPaymentService;

    private UUID customerId = UUID.randomUUID();
    private UUID userId = UUID.randomUUID();

    private Payment samplePayment(UUID id) {
        BillingCustomer customer = BillingCustomer.builder()
                .id(customerId)
                .userId(userId)
                .provider("STRIPE")
                .build();
        return Payment.builder()
                .id(id)
                .customer(customer)
                .amount(new BigDecimal("29.90"))
                .currency("BRL")
                .status(PaymentStatus.PENDING)
                .provider("STRIPE")
                .providerPaymentRef("pi_owned")
                .build();
    }

    @Test
    void getByIdReturnsPaymentForOwner() throws Exception {
        UUID id = UUID.randomUUID();
        when(getPaymentService.execute(id)).thenReturn(samplePayment(id));

        mockMvc.perform(get("/api/v1/payments/" + id)
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.providerPaymentRef").value("pi_owned"));
    }

    @Test
    void getByIdIsForbiddenForNonOwnerWithoutAdminScope() throws Exception {
        UUID id = UUID.randomUUID();
        when(getPaymentService.execute(id)).thenReturn(samplePayment(id));

        mockMvc.perform(get("/api/v1/payments/" + id)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByIdReturns404ForMissingPayment() throws Exception {
        UUID id = UUID.randomUUID();
        when(getPaymentService.execute(id)).thenThrow(new PaymentNotFoundException("payment not found"));

        mockMvc.perform(get("/api/v1/payments/" + id)
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listWithoutAdminScopeRequiresCustomerOrSubscriptionFilter() throws Exception {
        mockMvc.perform(get("/api/v1/payments")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listFiltersByCustomerId() throws Exception {
        Payment payment = samplePayment(UUID.randomUUID());
        when(getPaymentService.find(any(), any()))
                .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/payments")
                        .param("customerId", customerId.toString())
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].providerPaymentRef").value("pi_owned"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listWithAdminScopeDoesNotRequireCustomerFilter() throws Exception {
        Payment payment = samplePayment(UUID.randomUUID());
        when(getPaymentService.find(any(), any()))
                .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/payments")
                        .with(jwt().jwt(j -> j.subject(userId.toString())
                                .claim("scope", List.of("billing:admin")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void listFiltersByStatus() throws Exception {
        Payment payment = samplePayment(UUID.randomUUID());
        payment.setStatus(PaymentStatus.SUCCEEDED);
        when(getPaymentService.find(any(), any()))
                .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/payments")
                        .param("status", "SUCCEEDED")
                        .param("customerId", customerId.toString())
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"));
    }
}
