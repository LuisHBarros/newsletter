package com.assine.content.domain.notion.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Provider-neutral block representation used by the HTML renderer.
 * Only a subset of Notion block types is supported in v1.
 */
@Data
@Builder
public class NotionBlock {
    private String id;
    private Type type;
    /** Plain text content (or caption for media blocks). */
    private String text;
    /** URL for image/video/file blocks. */
    private String url;
    /** Language for code blocks. */
    private String language;
    /** Children (for nested lists, toggles, etc.). */
    private List<NotionBlock> children;

    public enum Type {
        PARAGRAPH,
        HEADING_1, HEADING_2, HEADING_3,
        BULLETED_LIST_ITEM, NUMBERED_LIST_ITEM,
        QUOTE, CALLOUT, DIVIDER,
        CODE,
        IMAGE,
        BOOKMARK,
        UNSUPPORTED
    }
}
