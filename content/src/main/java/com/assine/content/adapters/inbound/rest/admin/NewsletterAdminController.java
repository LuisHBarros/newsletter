package com.assine.content.adapters.inbound.rest.admin;

import com.assine.content.adapters.inbound.rest.admin.dto.NewsletterDtos.CreateNewsletterRequest;
import com.assine.content.adapters.inbound.rest.admin.dto.NewsletterDtos.NewsletterResponse;
import com.assine.content.adapters.inbound.rest.admin.dto.NewsletterDtos.UpdatePlansRequest;
import com.assine.content.application.newsletter.NewsletterService;
import com.assine.content.domain.newsletter.model.Newsletter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/newsletters")
@RequiredArgsConstructor
public class NewsletterAdminController {

    private final NewsletterService newsletterService;

    @PostMapping
    public ResponseEntity<NewsletterResponse> create(@RequestBody @Valid CreateNewsletterRequest req) {
        Newsletter n = newsletterService.create(
                req.slug(), req.name(), req.description(),
                req.notionDatabaseId(), req.defaultFromEmail(),
                req.planIds());
        NewsletterResponse body = NewsletterResponse.of(n, req.planIds());
        return ResponseEntity.created(URI.create("/api/v1/admin/newsletters/" + n.getId())).body(body);
    }

    @GetMapping
    public List<NewsletterResponse> list() {
        return newsletterService.listAll().stream()
                .map(n -> NewsletterResponse.of(n, newsletterService.getPlanIds(n.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public NewsletterResponse get(@PathVariable UUID id) {
        Newsletter n = newsletterService.getById(id);
        return NewsletterResponse.of(n, newsletterService.getPlanIds(id));
    }

    @PutMapping("/{id}/plans")
    public NewsletterResponse updatePlans(@PathVariable UUID id, @RequestBody @Valid UpdatePlansRequest req) {
        Newsletter n = newsletterService.updatePlans(id, req.planIds());
        return NewsletterResponse.of(n, req.planIds());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        newsletterService.archive(id);
        return ResponseEntity.noContent().build();
    }
}
