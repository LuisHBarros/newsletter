package com.assine.content.adapters.inbound.rest.admin;

import com.assine.content.application.issue.IssueImportService;
import com.assine.content.domain.issue.model.Issue;
import com.assine.content.domain.issue.repository.IssueRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/issues")
@RequiredArgsConstructor
public class IssueAdminController {

    private final IssueImportService importService;
    private final IssueRepository issueRepository;

    /** Force a re-import (and re-render) of a Notion page. */
    @PostMapping("/import")
    public IssueSummary importByNotionPageId(@RequestBody @jakarta.validation.Valid ImportRequest req) {
        IssueImportService.ImportResult r = importService.importByPageId(req.notionPageId());
        return IssueSummary.of(r.issue());
    }

    @GetMapping("/{id}")
    public IssueSummary get(@PathVariable UUID id) {
        Issue i = issueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + id));
        return IssueSummary.of(i);
    }

    @GetMapping
    public List<IssueSummary> byNewsletter(@RequestParam UUID newsletterId) {
        return issueRepository.findByNewsletterId(newsletterId).stream().map(IssueSummary::of).toList();
    }

    public record ImportRequest(@NotBlank String notionPageId) {}

    public record IssueSummary(
            UUID id, UUID newsletterId, String notionPageId,
            String title, String slug, String status,
            Instant scheduledAt, Instant publishedAt, Integer version,
            String htmlS3Key
    ) {
        public static IssueSummary of(Issue i) {
            return new IssueSummary(
                    i.getId(), i.getNewsletterId(), i.getNotionPageId(),
                    i.getTitle(), i.getSlug(), i.getStatus().name(),
                    i.getScheduledAt(), i.getPublishedAt(), i.getVersion(),
                    i.getHtmlS3Key());
        }
    }
}
