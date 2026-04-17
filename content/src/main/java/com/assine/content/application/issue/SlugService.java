package com.assine.content.application.issue;

import com.assine.content.domain.issue.repository.IssueRepository;
import com.github.slugify.Slugify;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves an {@code Issue}'s slug: uses the Notion-provided slug if valid,
 * falls back to {@code slugify(title)}, and appends a numeric suffix on collision
 * within the same newsletter.
 */
@Component
@RequiredArgsConstructor
public class SlugService {

    private static final Slugify SLUGIFY = Slugify.builder().lowerCase(true).locale(java.util.Locale.forLanguageTag("pt-BR")).build();
    private static final int MAX_ATTEMPTS = 50;

    private final IssueRepository issueRepository;

    public String resolve(UUID newsletterId, String notionSlug, String title) {
        String base = sanitize(notionSlug);
        if (base == null) {
            base = SLUGIFY.slugify(title != null ? title : "issue");
        }
        if (base.isBlank()) base = "issue";

        String candidate = base;
        for (int i = 2; i <= MAX_ATTEMPTS; i++) {
            if (!issueRepository.existsBySlugAndNewsletterId(candidate, newsletterId)) {
                return candidate;
            }
            candidate = base + "-" + i;
        }
        throw new IllegalStateException("Could not allocate unique slug for base '" + base + "'");
    }

    private String sanitize(String raw) {
        if (raw == null) return null;
        String slug = SLUGIFY.slugify(raw);
        return slug.isBlank() ? null : slug;
    }
}
