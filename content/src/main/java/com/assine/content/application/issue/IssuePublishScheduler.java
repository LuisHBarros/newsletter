package com.assine.content.application.issue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Calls {@link IssueScheduleService#publishDue()} on a cron. In prod this job is
 * disabled and the same endpoint is invoked by EventBridge Scheduler instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "content.scheduler.publish-scheduled", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IssuePublishScheduler {

    private final IssueScheduleService scheduleService;

    @Scheduled(cron = "${content.scheduler.publish-scheduled.cron:0 * * * * *}")
    @SchedulerLock(name = "content-publish-scheduled", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void run() {
        int published = scheduleService.publishDue();
        if (published > 0) {
            log.info("publish-scheduled cron: {} issue(s) published", published);
        }
    }
}
