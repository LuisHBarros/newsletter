package com.assine.billing.domain.outbox.repository;

import com.assine.billing.domain.outbox.model.ProcessedEvent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProcessedEventRepository {
    ProcessedEvent save(ProcessedEvent event);
    Optional<ProcessedEvent> findById(UUID eventId);
    boolean existsById(UUID eventId);

    /** Delete processed-event rows whose expires_at has passed. */
    int deleteExpired(Instant now);
}
