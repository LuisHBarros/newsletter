package com.assine.content.application.newsletter;

import com.assine.content.application.outbox.OutboxEventService;
import com.assine.content.domain.newsletter.model.Newsletter;
import com.assine.content.domain.newsletter.model.NewsletterPlan;
import com.assine.content.domain.newsletter.model.NewsletterStatus;
import com.assine.content.domain.newsletter.repository.NewsletterRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

public class NewsletterService {

    private final NewsletterRepository newsletterRepository;
    private final OutboxEventService outboxEventService;

    public NewsletterService(NewsletterRepository newsletterRepository,
                             OutboxEventService outboxEventService) {
        this.newsletterRepository = newsletterRepository;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public Newsletter create(String slug, String name, String description,
                             String notionDatabaseId, String defaultFromEmail,
                             List<UUID> planIds) {
        Newsletter n = newsletterRepository.save(Newsletter.builder()
                .id(UUID.randomUUID())
                .slug(slug)
                .name(name)
                .description(description)
                .notionDatabaseId(notionDatabaseId)
                .defaultFromEmail(defaultFromEmail)
                .status(NewsletterStatus.ACTIVE)
                .build());

        List<NewsletterPlan> plans = toPlans(n.getId(), planIds);
        newsletterRepository.replacePlans(n.getId(), plans);

        outboxEventService.createEvent(
                "content.newsletter.created",
                "Newsletter",
                n.getId(),
                Map.of(
                        "newsletterId", n.getId().toString(),
                        "slug", n.getSlug(),
                        "name", n.getName(),
                        "planIds", planIds.stream().map(UUID::toString).toList()
                ));
        return n;
    }

    @Transactional
    public Newsletter updatePlans(UUID newsletterId, List<UUID> planIds) {
        Newsletter n = newsletterRepository.findById(newsletterId)
                .orElseThrow(() -> new IllegalArgumentException("Newsletter not found: " + newsletterId));
        List<NewsletterPlan> plans = toPlans(n.getId(), planIds);
        newsletterRepository.replacePlans(n.getId(), plans);

        outboxEventService.createEvent(
                "content.newsletter.plans_updated",
                "Newsletter",
                n.getId(),
                Map.of(
                        "newsletterId", n.getId().toString(),
                        "planIds", planIds.stream().map(UUID::toString).toList()
                ));
        return n;
    }

    @Transactional
    public void archive(UUID newsletterId) {
        Newsletter n = newsletterRepository.findById(newsletterId)
                .orElseThrow(() -> new IllegalArgumentException("Newsletter not found: " + newsletterId));
        n.setStatus(NewsletterStatus.ARCHIVED);
        newsletterRepository.save(n);

        outboxEventService.createEvent(
                "content.newsletter.archived",
                "Newsletter",
                n.getId(),
                Map.of("newsletterId", n.getId().toString()));
    }

    public Newsletter getById(UUID id) {
        return newsletterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Newsletter not found: " + id));
    }

    public Optional<Newsletter> findBySlug(String slug) {
        return newsletterRepository.findBySlug(slug);
    }

    public List<Newsletter> listAll() { return newsletterRepository.findAll(); }

    public List<UUID> getPlanIds(UUID newsletterId) {
        return newsletterRepository.findPlanIdsByNewsletterId(newsletterId);
    }

    private List<NewsletterPlan> toPlans(UUID newsletterId, List<UUID> planIds) {
        List<NewsletterPlan> plans = new ArrayList<>(planIds.size());
        for (UUID planId : planIds) {
            plans.add(NewsletterPlan.builder()
                    .newsletterId(newsletterId)
                    .planId(planId)
                    .accessTier(NewsletterPlan.AccessTier.FULL)
                    .build());
        }
        return plans;
    }
}
