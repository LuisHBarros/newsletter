package com.assine.content.domain.newsletter.repository;

import com.assine.content.domain.newsletter.model.Newsletter;
import com.assine.content.domain.newsletter.model.NewsletterPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NewsletterRepository {
    Newsletter save(Newsletter newsletter);
    Optional<Newsletter> findById(UUID id);
    Optional<Newsletter> findBySlug(String slug);
    Optional<Newsletter> findByNotionDatabaseId(String notionDatabaseId);
    List<Newsletter> findAll();
    void delete(UUID id);

    List<NewsletterPlan> findPlansByNewsletterId(UUID newsletterId);
    void replacePlans(UUID newsletterId, List<NewsletterPlan> plans);
    List<UUID> findPlanIdsByNewsletterId(UUID newsletterId);
}
