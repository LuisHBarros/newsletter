package com.assine.subscriptions.domain.outbox.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    @Column(name = "processed_at", updatable = false)
    @Builder.Default
    private Instant processedAt = Instant.now();

    @Column(name = "expires_at")
    @Builder.Default
    private Instant expiresAt = Instant.now().plus(java.time.Duration.ofDays(30));
}
