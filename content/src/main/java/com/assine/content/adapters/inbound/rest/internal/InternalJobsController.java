package com.assine.content.adapters.inbound.rest.internal;

import com.assine.content.application.issue.IssueScheduleService;
import com.assine.content.application.issue.NotionReconciliationJob;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal endpoints triggered by EventBridge Scheduler in prod (API Gateway → ECS).
 * Require scope {@code content:admin}.
 */
@RestController
@RequestMapping("/api/v1/internal/jobs")
@RequiredArgsConstructor
public class InternalJobsController {

    private final IssueScheduleService scheduleService;
    private final NotionReconciliationJob reconciliationJob;

    @PostMapping("/publish-scheduled")
    public Map<String, Object> publishScheduled() {
        int published = scheduleService.publishDue();
        return Map.of("published", published);
    }

    @PostMapping("/reconcile-notion")
    public Map<String, Object> reconcileNotion() {
        int enqueued = reconciliationJob.reconcile();
        return Map.of("enqueued", enqueued);
    }
}
