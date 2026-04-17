package com.assine.billing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates sample payloads against the published JSON Schemas under {@code contracts/published/}.
 * These tests prevent silent contract drift when handlers change event payload shapes.
 */
class PublishedEventsContractTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaFactory factory =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private JsonSchema load(String path) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            assertThat(is).as("schema resource %s", path).isNotNull();
            return factory.getSchema(is);
        }
    }

    private Set<ValidationMessage> validate(String path, Object payload) throws Exception {
        JsonNode node = mapper.valueToTree(payload);
        return load(path).validate(node);
    }

    @Test
    void billingPaymentSucceededValidPayloadPasses() throws Exception {
        Map<String, Object> payload = Map.of(
            "paymentId", "11111111-1111-1111-1111-111111111111",
            "customerId", "22222222-2222-2222-2222-222222222222",
            "subscriptionId", "33333333-3333-3333-3333-333333333333",
            "amount", 29.90,
            "currency", "BRL",
            "currentPeriodStart", "2026-04-16T19:45:00Z",
            "currentPeriodEnd",   "2026-05-16T19:45:00Z",
            "providerPaymentRef", "pi_abc"
        );
        assertThat(validate("/contracts/published/billing.payment.succeeded.v1.json", payload)).isEmpty();
    }

    @Test
    void billingPaymentFailedMissingPaymentIdFails() throws Exception {
        Map<String, Object> payload = Map.of("attempts", 1, "reason", "card_declined");
        assertThat(validate("/contracts/published/billing.payment.failed.v1.json", payload)).isNotEmpty();
    }

    @Test
    void billingSubscriptionActivatedValidPayloadPasses() throws Exception {
        Map<String, Object> payload = Map.of(
            "subscriptionId", "33333333-3333-3333-3333-333333333333",
            "currentPeriodStart", "2026-04-16T19:45:00Z",
            "currentPeriodEnd",   "2026-05-16T19:45:00Z",
            "billingRef", "fake_sub_1"
        );
        assertThat(validate("/contracts/published/billing.subscription.activated.v1.json", payload)).isEmpty();
    }

    @Test
    void billingSubscriptionCanceledValidPayloadPasses() throws Exception {
        Map<String, Object> payload = Map.of(
            "subscriptionId", "33333333-3333-3333-3333-333333333333",
            "canceledAt", "2026-04-17T19:45:00Z",
            "reason", "user_requested"
        );
        assertThat(validate("/contracts/published/billing.subscription.canceled.v1.json", payload)).isEmpty();
    }
}
