package com.assine.content.domain.notion.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Simplified, provider-neutral representation of a Notion page as needed by content.
 * Contains only what the renderer and import service use; raw Notion API payload is
 * re-read from the port when richer data is needed.
 */
@Data
@Builder
public class NotionPage {
    private String pageId;
    private String databaseId;
    private String title;
    /** Optional `slug` property from the Notion page. Null if absent. */
    private String slug;
    /** Optional `summary` / `excerpt` property. */
    private String summary;
    /** Optional `scheduled_at` / `publish_at` property. */
    private Instant scheduledAt;
    /** Optional `published` toggle. */
    private boolean published;
    private Instant lastEditedAt;
    private String coverImageUrl;
    private List<NotionBlock> blocks;
    /** Raw property map for diagnostics / future use. */
    private Map<String, Object> rawProperties;
}
