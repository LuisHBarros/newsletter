package com.assine.content.domain.notion.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Normalized change notification received from a Notion webhook.
 */
@Data
@Builder
public class NotionChangeEvent {
    private String deliveryId;
    private String databaseId;
    private String pageId;
    private Instant lastEditedAt;
    private String changeType; // page.created | page.updated | page.deleted | unknown
}
