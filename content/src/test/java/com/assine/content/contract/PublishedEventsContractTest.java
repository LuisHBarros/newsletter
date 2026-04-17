package com.assine.content.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates sample published event envelopes against the JSON Schemas under
 * {@code resources/contracts/}. Catches silent contract drift when services
 * change their payload shapes.
 */
class PublishedEventsContractTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaFactory factory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private JsonSchema load(String schemaFile) throws Exception {
        String path = "/contracts/" + schemaFile;
        try (InputStream is = getClass().getResourceAsStream(path)) {
            assertThat(is).as("schema resource %s", path).isNotNull();
            return factory.getSchema(is);
        }
    }

    private Set<ValidationMessage> validate(String schemaFile, Object envelope) throws Exception {
        JsonNode node = mapper.valueToTree(envelope);
        return load(schemaFile).validate(node);
    }

    private Map<String, Object> envelope(String eventType,
                                         String aggregateType,
                                         UUID aggregateId,
                                         Map<String, Object> payload) {
        return Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", eventType,
                "schemaVersion", 1,
                "aggregateType", aggregateType,
                "aggregateId", aggregateId.toString(),
                "occurredAt", Instant.parse("2026-04-17T12:00:00Z").toString(),
                "payload", payload
        );
    }

    @Test
    void newsletterCreatedValidEnvelopePasses() throws Exception {
        UUID newsletterId = UUID.randomUUID();
        Map<String, Object> env = envelope(
                "content.newsletter.created", "Newsletter", newsletterId,
                Map.of(
                        "newsletterId", newsletterId.toString(),
                        "slug", "tech",
                        "name", "Tech Weekly",
                        "planIds", List.of(UUID.randomUUID().toString())
                ));
        assertThat(validate("content.newsletter.created.schema.json", env)).isEmpty();
    }

    @Test
    void newsletterCreatedMissingSlugFails() throws Exception {
        UUID newsletterId = UUID.randomUUID();
        Map<String, Object> env = envelope(
                "content.newsletter.created", "Newsletter", newsletterId,
                Map.of(
                        "newsletterId", newsletterId.toString(),
                        "name", "Tech Weekly",
                        "planIds", List.of()
                ));
        assertThat(validate("content.newsletter.created.schema.json", env)).isNotEmpty();
    }

    @Test
    void newsletterPlansUpdatedValidEnvelopePasses() throws Exception {
        UUID newsletterId = UUID.randomUUID();
        Map<String, Object> env = envelope(
                "content.newsletter.plans_updated", "Newsletter", newsletterId,
                Map.of(
                        "newsletterId", newsletterId.toString(),
                        "planIds", List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString())
                ));
        assertThat(validate("content.newsletter.plans_updated.schema.json", env)).isEmpty();
    }

    @Test
    void newsletterArchivedValidEnvelopePasses() throws Exception {
        UUID newsletterId = UUID.randomUUID();
        Map<String, Object> env = envelope(
                "content.newsletter.archived", "Newsletter", newsletterId,
                Map.of("newsletterId", newsletterId.toString()));
        assertThat(validate("content.newsletter.archived.schema.json", env)).isEmpty();
    }

    @Test
    void issuePublishedValidEnvelopePasses() throws Exception {
        UUID issueId = UUID.randomUUID();
        UUID newsletterId = UUID.randomUUID();
        Map<String, Object> env = envelope(
                "content.issue.published", "Issue", issueId,
                Map.of(
                        "newsletterId", newsletterId.toString(),
                        "newsletterSlug", "tech",
                        "issueId", issueId.toString(),
                        "title", "Edição de Abril",
                        "slug", "edicao-de-abril",
                        "htmlS3Key", "content/tech/" + issueId + "/v1/index.html",
                        "publishedAt", Instant.parse("2026-04-17T12:00:00Z").toString(),
                        "planIds", List.of(UUID.randomUUID().toString())
                ));
        assertThat(validate("content.issue.published.schema.json", env)).isEmpty();
    }

    @Test
    void issuePublishedMissingHtmlKeyFails() throws Exception {
        UUID issueId = UUID.randomUUID();
        UUID newsletterId = UUID.randomUUID();
        Map<String, Object> env = envelope(
                "content.issue.published", "Issue", issueId,
                Map.of(
                        "newsletterId", newsletterId.toString(),
                        "newsletterSlug", "tech",
                        "issueId", issueId.toString(),
                        "title", "X",
                        "slug", "x",
                        "publishedAt", Instant.parse("2026-04-17T12:00:00Z").toString(),
                        "planIds", List.of()
                ));
        assertThat(validate("content.issue.published.schema.json", env)).isNotEmpty();
    }

    @Test
    void issueUpdatedValidEnvelopePasses() throws Exception {
        UUID issueId = UUID.randomUUID();
        UUID newsletterId = UUID.randomUUID();
        Map<String, Object> env = envelope(
                "content.issue.updated", "Issue", issueId,
                Map.of(
                        "newsletterId", newsletterId.toString(),
                        "newsletterSlug", "tech",
                        "issueId", issueId.toString(),
                        "version", 2,
                        "title", "Edição de Abril",
                        "slug", "edicao-de-abril",
                        "htmlS3Key", "content/tech/" + issueId + "/v1/index.html",
                        "publishedAt", Instant.parse("2026-04-17T12:00:00Z").toString(),
                        "planIds", List.of(UUID.randomUUID().toString())
                ));
        assertThat(validate("content.issue.updated.schema.json", env)).isEmpty();
    }

    @Test
    void issueUpdatedRejectsVersionZero() throws Exception {
        UUID issueId = UUID.randomUUID();
        UUID newsletterId = UUID.randomUUID();
        Map<String, Object> env = envelope(
                "content.issue.updated", "Issue", issueId,
                Map.of(
                        "newsletterId", newsletterId.toString(),
                        "newsletterSlug", "tech",
                        "issueId", issueId.toString(),
                        "version", 0,
                        "title", "X",
                        "slug", "x",
                        "htmlS3Key", "content/tech/" + issueId + "/v1/index.html",
                        "publishedAt", Instant.parse("2026-04-17T12:00:00Z").toString(),
                        "planIds", List.of()
                ));
        assertThat(validate("content.issue.updated.schema.json", env)).isNotEmpty();
    }

    @Test
    void issueUpdatedMissingHtmlS3KeyFails() throws Exception {
        UUID issueId = UUID.randomUUID();
        UUID newsletterId = UUID.randomUUID();
        Map<String, Object> env = envelope(
                "content.issue.updated", "Issue", issueId,
                Map.of(
                        "newsletterId", newsletterId.toString(),
                        "newsletterSlug", "tech",
                        "issueId", issueId.toString(),
                        "version", 1,
                        "title", "X",
                        "slug", "x",
                        "publishedAt", Instant.parse("2026-04-17T12:00:00Z").toString(),
                        "planIds", List.of()
                ));
        assertThat(validate("content.issue.updated.schema.json", env)).isNotEmpty();
    }

    @Test
    void issueUpdatedMissingTitleFails() throws Exception {
        UUID issueId = UUID.randomUUID();
        UUID newsletterId = UUID.randomUUID();
        Map<String, Object> env = envelope(
                "content.issue.updated", "Issue", issueId,
                Map.of(
                        "newsletterId", newsletterId.toString(),
                        "newsletterSlug", "tech",
                        "issueId", issueId.toString(),
                        "version", 1,
                        "slug", "x",
                        "htmlS3Key", "content/tech/" + issueId + "/v1/index.html",
                        "publishedAt", Instant.parse("2026-04-17T12:00:00Z").toString(),
                        "planIds", List.of()
                ));
        assertThat(validate("content.issue.updated.schema.json", env)).isNotEmpty();
    }

    @Test
    void wrongEventTypeFailsConstCheck() throws Exception {
        UUID newsletterId = UUID.randomUUID();
        Map<String, Object> env = envelope(
                "content.newsletter.archived", "Newsletter", newsletterId,
                Map.of("newsletterId", newsletterId.toString()));
        // tamper eventType
        var mutable = new java.util.HashMap<String, Object>(env);
        mutable.put("eventType", "content.newsletter.created");
        assertThat(validate("content.newsletter.archived.schema.json", mutable)).isNotEmpty();
    }
}
