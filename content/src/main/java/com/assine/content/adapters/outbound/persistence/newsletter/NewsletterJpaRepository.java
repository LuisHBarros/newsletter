package com.assine.content.adapters.outbound.persistence.newsletter;

import com.assine.content.domain.newsletter.model.Newsletter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NewsletterJpaRepository extends JpaRepository<Newsletter, UUID> {
    Optional<Newsletter> findBySlug(String slug);
    Optional<Newsletter> findByNotionDatabaseId(String notionDatabaseId);
}
