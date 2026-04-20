package com.assine.billing.adapters.inbound.rest.webhook;

import com.assine.billing.application.payment.StripeWebhookService;
import com.assine.billing.config.StripeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StripeWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(StripeWebhookControllerTest.TestStripeProperties.class)
class StripeWebhookControllerTest {

    private static final String SECRET = "whsec_test_secret_0123456789";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private StripeWebhookService webhookService;

    @Test
    void returnsBadRequestWhenSignatureHeaderMissing() throws Exception {
        mockMvc.perform(post("/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing Stripe-Signature header"));
    }

    @Test
    void returnsBadRequestWhenSignatureInvalid() throws Exception {
        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", "t=1,v1=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid signature"));
        verifyNoInteractions(webhookService);
    }

    @Test
    void dispatchesPaymentIntentSucceeded() throws Exception {
        String eventId = "evt_succ_" + UUID.randomUUID();
        String payload = paymentIntentEvent(eventId, "payment_intent.succeeded", "pi_123",
                Map.of("paymentId", UUID.randomUUID().toString()));
        String sigHeader = signHeader(payload, System.currentTimeMillis() / 1000L, SECRET);

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", sigHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(webhookService).processWithDedup(any(com.stripe.model.Event.class));
    }

    @Test
    void dispatchesPaymentIntentFailed() throws Exception {
        String eventId = "evt_fail_" + UUID.randomUUID();
        String payload = paymentIntentEvent(eventId, "payment_intent.payment_failed", "pi_456",
                Map.of("paymentId", UUID.randomUUID().toString()));
        String sigHeader = signHeader(payload, System.currentTimeMillis() / 1000L, SECRET);

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", sigHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(webhookService).processWithDedup(any(com.stripe.model.Event.class));
    }

    @Test
    void duplicateEventsReturnOkAndAreNotRetried() throws Exception {
        String eventId = "evt_dup_" + UUID.randomUUID();
        String payload = paymentIntentEvent(eventId, "payment_intent.succeeded", "pi_789", Map.of());
        String sigHeader = signHeader(payload, System.currentTimeMillis() / 1000L, SECRET);

        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(webhookService).processWithDedup(any(com.stripe.model.Event.class));

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", sigHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("duplicate"));
    }

    @Test
    void unknownEventTypeReturnsOkAndIsIgnored() throws Exception {
        String eventId = "evt_unknown_" + UUID.randomUUID();
        String payload = paymentIntentEvent(eventId, "customer.updated", "cus_xxx", Map.of());
        String sigHeader = signHeader(payload, System.currentTimeMillis() / 1000L, SECRET);

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", sigHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(webhookService).processWithDedup(any(com.stripe.model.Event.class));
    }

    @Test
    void deterministicUuidIsStableForSameEventId() {
        UUID a = StripeWebhookService.deterministicUuid("evt_same");
        UUID b = StripeWebhookService.deterministicUuid("evt_same");
        UUID c = StripeWebhookService.deterministicUuid("evt_other");
        org.assertj.core.api.Assertions.assertThat(a).isEqualTo(b);
        org.assertj.core.api.Assertions.assertThat(a).isNotEqualTo(c);
        org.assertj.core.api.Assertions.assertThat(a.version()).isEqualTo(5);
    }

    @Test
    void deterministicUuidIsDeterministicAcrossRuns() {
        UUID snapshot = UUID.fromString("09dd8c49-e27a-5359-b811-0fe3dfd3150d");
        UUID actual = StripeWebhookService.deterministicUuid("evt_snapshot_test");
        org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(snapshot);
    }

    private String paymentIntentEvent(String id, String type, String piId,
                                      Map<String, String> metadata) throws Exception {
        Map<String, Object> event = Map.of(
                "id", id,
                "object", "event",
                "api_version", "2024-06-20",
                "created", System.currentTimeMillis() / 1000L,
                "type", type,
                "data", Map.of("object", Map.of(
                        "id", piId,
                        "object", type.startsWith("payment_intent") ? "payment_intent" : "customer",
                        "amount", 2990,
                        "currency", "brl",
                        "status", type.endsWith("succeeded") ? "succeeded" : "requires_payment_method",
                        "metadata", metadata
                ))
        );
        return objectMapper.writeValueAsString(event);
    }

    static String signHeader(String payload, long timestamp, String secret) throws Exception {
        String signedPayload = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : sig) hex.append(String.format("%02x", b));
        return "t=" + timestamp + ",v1=" + hex;
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestStripeProperties {
        @org.springframework.context.annotation.Bean
        StripeProperties stripeProperties() {
            return new StripeProperties(true, "sk_test_dummy", SECRET);
        }
    }
}
