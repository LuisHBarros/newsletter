package com.assine.content.adapters.outbound.persistence.outbox;

import com.assine.content.domain.outbox.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEvent, UUID> {

    @Modifying
    @Query("DELETE FROM ProcessedEvent p WHERE p.expiresAt < CURRENT_TIMESTAMP")
    int deleteExpired();
}
