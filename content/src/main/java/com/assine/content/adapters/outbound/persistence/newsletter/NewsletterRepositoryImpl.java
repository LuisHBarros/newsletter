package com.assine.content.adapters.outbound.persistence.newsletter;

import com.assine.content.domain.newsletter.model.Newsletter;
import com.assine.content.domain.newsletter.model.NewsletterPlan;
import com.assine.content.domain.newsletter.repository.NewsletterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NewsletterRepositoryImpl implements NewsletterRepository {

    private final NewsletterJpaRepository newsletterJpa;
    private final NewsletterPlanJpaRepository planJpa;

    @Override @Transactional
    public Newsletter save(Newsletter newsletter) { return newsletterJpa.save(newsletter); }

    @Override
    public Optional<Newsletter> findById(UUID id) { return newsletterJpa.findById(id); }

    @Override
    public Optional<Newsletter> findBySlug(String slug) { return newsletterJpa.findBySlug(slug); }

    @Override
    public Optional<Newsletter> findByNotionDatabaseId(String notionDatabaseId) {
        return newsletterJpa.findByNotionDatabaseId(notionDatabaseId);
    }

    @Override
    public List<Newsletter> findAll() { return newsletterJpa.findAll(); }

    @Override @Transactional
    public void delete(UUID id) { newsletterJpa.deleteById(id); }

    @Override
    public List<NewsletterPlan> findPlansByNewsletterId(UUID newsletterId) {
        return planJpa.findByNewsletterId(newsletterId);
    }

    @Override
    public List<UUID> findPlanIdsByNewsletterId(UUID newsletterId) {
        return planJpa.findPlanIdsByNewsletterId(newsletterId);
    }

    @Override @Transactional
    public void replacePlans(UUID newsletterId, List<NewsletterPlan> plans) {
        planJpa.deleteByNewsletterId(newsletterId);
        planJpa.flush();
        for (NewsletterPlan p : plans) {
            p.setNewsletterId(newsletterId);
            planJpa.save(p);
        }
    }
}
