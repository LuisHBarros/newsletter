package com.assine.content.domain.notion.port;

import com.assine.content.domain.notion.model.NotionPage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotionPort {
    /** Fetch a page with its blocks by Notion pageId. */
    Optional<NotionPage> fetchPage(String pageId);

    /** Query a database for pages edited strictly after the given instant. */
    List<NotionPageSummary> queryRecentlyEdited(String databaseId, Instant since, int limit);

    /** Minimal summary used by reconciliation to decide what to import. */
    record NotionPageSummary(String pageId, Instant lastEditedAt) {}
}
