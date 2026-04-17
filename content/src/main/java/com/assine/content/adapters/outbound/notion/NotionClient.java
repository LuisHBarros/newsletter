package com.assine.content.adapters.outbound.notion;

import com.assine.content.domain.notion.model.NotionBlock;
import com.assine.content.domain.notion.model.NotionPage;
import com.assine.content.domain.notion.port.NotionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.*;

/**
 * Notion API adapter implementing {@link NotionPort}.
 * <p>Maps Notion's REST API payload (pages + children blocks) into the provider-neutral
 * {@link NotionPage} / {@link NotionBlock} used by the domain. Only the block types
 * listed in {@link NotionBlock.Type} are recognized; unsupported blocks map to
 * {@code UNSUPPORTED} and are rendered as a passthrough placeholder.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotionClient implements NotionPort {

    private final WebClient notionWebClient;

    @Override
    public Optional<NotionPage> fetchPage(String pageId) {
        try {
            Map<String, Object> page = notionWebClient.get()
                    .uri("/pages/{id}", pageId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (page == null) return Optional.empty();

            List<NotionBlock> blocks = fetchChildren(pageId);
            return Optional.of(mapPage(pageId, page, blocks));
        } catch (WebClientResponseException.NotFound nf) {
            log.warn("Notion page {} not found", pageId);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch Notion page {}", pageId, e);
            throw new RuntimeException("Notion fetchPage failed", e);
        }
    }

    @Override
    public List<NotionPageSummary> queryRecentlyEdited(String databaseId, Instant since, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page_size", Math.min(limit, 100));
        if (since != null) {
            body.put("filter", Map.of(
                    "timestamp", "last_edited_time",
                    "last_edited_time", Map.of("after", since.toString())
            ));
        }
        body.put("sorts", List.of(Map.of("timestamp", "last_edited_time", "direction", "ascending")));

        try {
            Map<String, Object> resp = notionWebClient.post()
                    .uri("/databases/{id}/query", databaseId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (resp == null) return List.of();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) resp.getOrDefault("results", List.of());
            List<NotionPageSummary> out = new ArrayList<>(results.size());
            for (Map<String, Object> p : results) {
                String pageId = (String) p.get("id");
                Instant lastEdited = parseInstant((String) p.get("last_edited_time"));
                out.add(new NotionPageSummary(pageId, lastEdited));
            }
            return out;
        } catch (Exception e) {
            log.error("Failed to query Notion database {} since {}", databaseId, since, e);
            throw new RuntimeException("Notion query failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<NotionBlock> fetchChildren(String blockId) {
        List<NotionBlock> out = new ArrayList<>();
        String cursor = null;
        do {
            String uri = "/blocks/" + blockId + "/children?page_size=100"
                    + (cursor != null ? "&start_cursor=" + cursor : "");
            Map<String, Object> resp = notionWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (resp == null) break;

            List<Map<String, Object>> results = (List<Map<String, Object>>) resp.getOrDefault("results", List.of());
            for (Map<String, Object> b : results) {
                out.add(mapBlock(b));
            }
            Boolean hasMore = (Boolean) resp.get("has_more");
            cursor = hasMore != null && hasMore ? (String) resp.get("next_cursor") : null;
        } while (cursor != null);
        return out;
    }

    @SuppressWarnings("unchecked")
    private NotionBlock mapBlock(Map<String, Object> raw) {
        String id = (String) raw.get("id");
        String type = (String) raw.get("type");
        Map<String, Object> body = (Map<String, Object>) raw.getOrDefault(type, Map.of());

        NotionBlock.Type mapped = switch (type) {
            case "paragraph" -> NotionBlock.Type.PARAGRAPH;
            case "heading_1" -> NotionBlock.Type.HEADING_1;
            case "heading_2" -> NotionBlock.Type.HEADING_2;
            case "heading_3" -> NotionBlock.Type.HEADING_3;
            case "bulleted_list_item" -> NotionBlock.Type.BULLETED_LIST_ITEM;
            case "numbered_list_item" -> NotionBlock.Type.NUMBERED_LIST_ITEM;
            case "quote" -> NotionBlock.Type.QUOTE;
            case "callout" -> NotionBlock.Type.CALLOUT;
            case "divider" -> NotionBlock.Type.DIVIDER;
            case "code" -> NotionBlock.Type.CODE;
            case "image" -> NotionBlock.Type.IMAGE;
            case "bookmark" -> NotionBlock.Type.BOOKMARK;
            default -> NotionBlock.Type.UNSUPPORTED;
        };

        String text = extractRichText(body.get("rich_text"));
        String url = null;
        String language = null;

        if (mapped == NotionBlock.Type.IMAGE) {
            Map<String, Object> file = (Map<String, Object>) body.getOrDefault("file", body.getOrDefault("external", Map.of()));
            url = (String) file.get("url");
            text = extractRichText(body.get("caption"));
        } else if (mapped == NotionBlock.Type.BOOKMARK) {
            url = (String) body.get("url");
            text = extractRichText(body.get("caption"));
        } else if (mapped == NotionBlock.Type.CODE) {
            language = (String) body.get("language");
        }

        Boolean hasChildren = (Boolean) raw.get("has_children");
        List<NotionBlock> children = Boolean.TRUE.equals(hasChildren) ? fetchChildren(id) : List.of();

        return NotionBlock.builder()
                .id(id)
                .type(mapped)
                .text(text)
                .url(url)
                .language(language)
                .children(children)
                .build();
    }

    @SuppressWarnings("unchecked")
    private String extractRichText(Object richTextField) {
        if (!(richTextField instanceof List<?> list)) return "";
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Object plain = m.get("plain_text");
                if (plain != null) sb.append(plain);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private NotionPage mapPage(String pageId, Map<String, Object> raw, List<NotionBlock> blocks) {
        Map<String, Object> props = (Map<String, Object>) raw.getOrDefault("properties", Map.of());
        String databaseId = extractDatabaseId(raw);
        Instant lastEdited = parseInstant((String) raw.get("last_edited_time"));
        String coverUrl = extractCoverUrl((Map<String, Object>) raw.get("cover"));

        return NotionPage.builder()
                .pageId(pageId)
                .databaseId(databaseId)
                .title(extractTitle(props))
                .slug(extractPlainTextProp(props, "slug"))
                .summary(extractPlainTextProp(props, "summary"))
                .scheduledAt(extractDateProp(props, "scheduled_at"))
                .published(Boolean.TRUE.equals(extractCheckbox(props, "published")))
                .lastEditedAt(lastEdited)
                .coverImageUrl(coverUrl)
                .blocks(blocks)
                .rawProperties(props)
                .build();
    }

    @SuppressWarnings("unchecked")
    private String extractDatabaseId(Map<String, Object> raw) {
        Map<String, Object> parent = (Map<String, Object>) raw.getOrDefault("parent", Map.of());
        return (String) parent.get("database_id");
    }

    @SuppressWarnings("unchecked")
    private String extractTitle(Map<String, Object> props) {
        for (Object v : props.values()) {
            if (v instanceof Map<?, ?> m && "title".equals(m.get("type"))) {
                return extractRichText(m.get("title"));
            }
        }
        return "Untitled";
    }

    @SuppressWarnings("unchecked")
    private String extractPlainTextProp(Map<String, Object> props, String name) {
        Object raw = props.get(name);
        if (!(raw instanceof Map<?, ?> m)) return null;
        String type = (String) m.get("type");
        if (type == null) return null;
        return switch (type) {
            case "rich_text" -> nullIfBlank(extractRichText(m.get("rich_text")));
            case "title" -> nullIfBlank(extractRichText(m.get("title")));
            case "url" -> (String) m.get("url");
            case "email" -> (String) m.get("email");
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private Instant extractDateProp(Map<String, Object> props, String name) {
        Object raw = props.get(name);
        if (!(raw instanceof Map<?, ?> m)) return null;
        Object dateObj = m.get("date");
        if (!(dateObj instanceof Map<?, ?> d)) return null;
        Object start = d.get("start");
        return start instanceof String s ? parseInstant(s) : null;
    }

    private Boolean extractCheckbox(Map<String, Object> props, String name) {
        Object raw = props.get(name);
        if (!(raw instanceof Map<?, ?> m)) return null;
        Object val = m.get("checkbox");
        return val instanceof Boolean b ? b : null;
    }

    @SuppressWarnings("unchecked")
    private String extractCoverUrl(Map<String, Object> cover) {
        if (cover == null) return null;
        String type = (String) cover.get("type");
        if (type == null) return null;
        Map<String, Object> body = (Map<String, Object>) cover.get(type);
        return body != null ? (String) body.get("url") : null;
    }

    private Instant parseInstant(String iso) {
        if (iso == null) return null;
        try {
            return Instant.parse(iso.length() == 10 ? iso + "T00:00:00Z" : iso);
        } catch (Exception e) {
            return null;
        }
    }

    private String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
