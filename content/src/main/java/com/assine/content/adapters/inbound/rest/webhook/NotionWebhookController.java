package com.assine.content.adapters.inbound.rest.webhook;

import com.assine.content.application.webhook.NotionWebhookService;
import com.assine.content.config.NotionProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives Notion webhooks. Signature verified from raw body bytes read via the
 * {@link RawBodyWrappingFilter}. JSON body is parsed after verification succeeds.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/notion")
@RequiredArgsConstructor
public class NotionWebhookController {

    private final NotionWebhookService webhookService;
    private final NotionProperties notionProperties;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> receive(HttpServletRequest request) throws Exception {
        byte[] body = (byte[]) request.getAttribute(RawBodyWrappingFilter.RAW_BODY_ATTR);
        if (body == null) body = request.getInputStream().readAllBytes();

        String header = firstNonBlank(
                request.getHeader("X-Notion-Signature"),
                request.getHeader("Notion-Signature")
        );
        boolean valid = HmacSignatureVerifier.verify(
                notionProperties.getWebhookSecret(), body, header);

        Map<String, Object> payload = body.length == 0
                ? Map.of()
                : objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});

        String deliveryId = firstNonBlank(
                request.getHeader("X-Notion-Delivery-Id"),
                request.getHeader("Notion-Delivery-Id"),
                request.getHeader("X-Request-Id")
        );

        NotionWebhookService.Outcome outcome = webhookService.handle(deliveryId, valid, payload);

        return switch (outcome) {
            case ACCEPTED -> ResponseEntity.accepted().body(Map.of("status", "accepted"));
            case DUPLICATE -> ResponseEntity.ok(Map.of("status", "duplicate"));
            case INVALID -> ResponseEntity.status(401).body(Map.of("status", "invalid"));
        };
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
