package com.assine.content.adapters.inbound.rest.admin.dto;

import com.assine.content.domain.newsletter.model.Newsletter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NewsletterDtos {

    private NewsletterDtos() {}

    public record CreateNewsletterRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$") String slug,
            @NotBlank String name,
            String description,
            @NotBlank String notionDatabaseId,
            String defaultFromEmail,
            @NotNull List<UUID> planIds
    ) {}

    public record UpdatePlansRequest(@NotNull List<UUID> planIds) {}

    public record NewsletterResponse(
            UUID id,
            String slug,
            String name,
            String description,
            String status,
            String notionDatabaseId,
            String defaultFromEmail,
            List<UUID> planIds,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static NewsletterResponse of(Newsletter n, List<UUID> planIds) {
            return new NewsletterResponse(
                    n.getId(), n.getSlug(), n.getName(), n.getDescription(),
                    n.getStatus().name(), n.getNotionDatabaseId(), n.getDefaultFromEmail(),
                    planIds, n.getCreatedAt(), n.getUpdatedAt());
        }
    }
}
