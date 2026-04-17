package com.assine.billing.adapters.outbound.persistence.outbox;

import com.assine.billing.domain.outbox.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEvent, UUID> {

    @Modifying
    @Query("DELETE FROM ProcessedEvent p WHERE p.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") Instant now);
}
