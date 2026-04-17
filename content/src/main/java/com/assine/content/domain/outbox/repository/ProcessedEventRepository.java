package com.assine.content.domain.outbox.repository;

import com.assine.content.domain.outbox.model.ProcessedEvent;

import java.util.Optional;
import java.util.UUID;

public interface ProcessedEventRepository {
    ProcessedEvent save(ProcessedEvent event);
    Optional<ProcessedEvent> findById(UUID eventId);
    boolean existsById(UUID eventId);
    int deleteExpired();
}
