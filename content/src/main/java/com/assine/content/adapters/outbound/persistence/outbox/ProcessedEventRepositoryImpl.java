package com.assine.content.adapters.outbound.persistence.outbox;

import com.assine.content.domain.outbox.model.ProcessedEvent;
import com.assine.content.domain.outbox.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProcessedEventRepositoryImpl implements ProcessedEventRepository {

    private final ProcessedEventJpaRepository jpa;

    @Override
    @Transactional
    public ProcessedEvent save(ProcessedEvent event) { return jpa.save(event); }

    @Override
    public Optional<ProcessedEvent> findById(UUID eventId) { return jpa.findById(eventId); }

    @Override
    public boolean existsById(UUID eventId) { return jpa.existsById(eventId); }

    @Override
    @Transactional
    public int deleteExpired() { return jpa.deleteExpired(); }
}
