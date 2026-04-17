package com.assine.content.domain.webhook.model;

import com.assine.content.domain.outbox.model.JsonMapConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notion_webhook_deliveries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotionWebhookDelivery {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "delivery_id", nullable = false, unique = true, length = 128)
    private String deliveryId;

    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private Status status = Status.RECEIVED;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "received_at", updatable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    public enum Status { RECEIVED, ACCEPTED, REJECTED, FAILED }
}
