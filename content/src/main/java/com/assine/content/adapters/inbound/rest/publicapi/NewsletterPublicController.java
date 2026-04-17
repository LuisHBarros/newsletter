package com.assine.content.adapters.inbound.rest.publicapi;

import com.assine.content.adapters.inbound.rest.admin.IssueAdminController.IssueSummary;
import com.assine.content.application.newsletter.NewsletterService;
import com.assine.content.domain.issue.model.IssueStatus;
import com.assine.content.domain.issue.repository.IssueRepository;
import com.assine.content.domain.newsletter.model.Newsletter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Read-only endpoints for authenticated subscribers (scope {@code content:read}).
 * Access to the actual HTML artifact is delegated to the {@code access} service,
 * which issues pre-signed S3 URLs based on plan entitlement.
 */
@RestController
@RequestMapping("/api/v1/newsletters")
@RequiredArgsConstructor
public class NewsletterPublicController {

    private final NewsletterService newsletterService;
    private final IssueRepository issueRepository;

    @GetMapping
    public List<PublicNewsletter> list() {
        return newsletterService.listAll().stream()
                .map(n -> PublicNewsletter.of(n, newsletterService.getPlanIds(n.getId())))
                .toList();
    }

    @GetMapping("/{slug}")
    public PublicNewsletter getBySlug(@PathVariable String slug) {
        Newsletter n = newsletterService.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Newsletter not found: " + slug));
        return PublicNewsletter.of(n, newsletterService.getPlanIds(n.getId()));
    }

    @GetMapping("/{slug}/issues")
    public List<IssueSummary> listIssues(@PathVariable String slug) {
        Newsletter n = newsletterService.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Newsletter not found: " + slug));
        return issueRepository.findByNewsletterIdAndStatus(n.getId(), IssueStatus.PUBLISHED)
                .stream().map(IssueSummary::of).toList();
    }

    public record PublicNewsletter(UUID id, String slug, String name, String description,
                                    String status, List<UUID> planIds) {
        static PublicNewsletter of(Newsletter n, List<UUID> planIds) {
            return new PublicNewsletter(n.getId(), n.getSlug(), n.getName(),
                    n.getDescription(), n.getStatus().name(), planIds);
        }
    }
}
