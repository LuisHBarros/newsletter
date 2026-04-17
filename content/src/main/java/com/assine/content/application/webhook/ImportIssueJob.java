package com.assine.content.application.webhook;

import java.time.Instant;

/** Message payload for the {@code assine-content-jobs} queue. */
public record ImportIssueJob(String pageId, Instant lastEditedAt, String source) {
}
