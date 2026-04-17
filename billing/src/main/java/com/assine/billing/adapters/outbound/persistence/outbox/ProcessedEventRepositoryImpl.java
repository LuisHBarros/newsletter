package com.assine.billing.adapters.outbound.persistence.outbox;

import com.assine.billing.domain.outbox.model.ProcessedEvent;
import com.assine.billing.domain.outbox.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProcessedEventRepositoryImpl implements ProcessedEventRepository {

    private final ProcessedEventJpaRepository jpaRepository;

    @Override
    public ProcessedEvent save(ProcessedEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public Optional<ProcessedEvent> findById(UUID eventId) {
        return jpaRepository.findById(eventId);
    }

    @Override
    public boolean existsById(UUID eventId) {
        return jpaRepository.existsById(eventId);
    }

    @Override
    @Transactional
    public int deleteExpired(Instant now) {
        return jpaRepository.deleteByExpiresAtBefore(now);
    }
}
