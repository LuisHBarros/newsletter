package com.assine.content.adapters.outbound.render;

import com.assine.content.domain.notion.model.NotionBlock;
import com.assine.content.domain.notion.model.NotionPage;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts a {@link NotionPage} (already imported via {@link com.assine.content.domain.notion.port.NotionPort})
 * into semantic, pt-BR HTML suitable for storing in S3 and emailing via notifications.
 * <p>Intentionally minimalistic: no CSS framework, no PDF — just well-structured HTML.
 */
@Component
public class HtmlRenderer {

    /** Returns a fully self-contained HTML document (<!DOCTYPE html> … </html>). */
    public String renderDocument(NotionPage page) {
        StringBuilder body = new StringBuilder(4096);
        body.append("<article class=\"newsletter-issue\" lang=\"pt-BR\">\n");
        body.append("  <header>\n");
        body.append("    <h1>").append(escape(page.getTitle())).append("</h1>\n");
        if (page.getSummary() != null && !page.getSummary().isBlank()) {
            body.append("    <p class=\"summary\">").append(escape(page.getSummary())).append("</p>\n");
        }
        if (page.getCoverImageUrl() != null) {
            body.append("    <figure class=\"cover\"><img src=\"")
                    .append(escapeAttr(page.getCoverImageUrl())).append("\" alt=\"\"/></figure>\n");
        }
        body.append("  </header>\n");
        body.append("  <section class=\"body\">\n");
        renderBlocks(body, page.getBlocks() != null ? page.getBlocks() : List.of(), "    ");
        body.append("  </section>\n");
        body.append("</article>\n");

        return "<!DOCTYPE html>\n" +
                "<html lang=\"pt-BR\">\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\"/>\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>\n" +
                "  <title>" + escape(page.getTitle()) + "</title>\n" +
                "</head>\n" +
                "<body>\n" +
                body +
                "</body>\n" +
                "</html>\n";
    }

    private void renderBlocks(StringBuilder out, List<NotionBlock> blocks, String indent) {
        int i = 0;
        while (i < blocks.size()) {
            NotionBlock b = blocks.get(i);
            NotionBlock.Type t = b.getType();

            if (t == NotionBlock.Type.BULLETED_LIST_ITEM || t == NotionBlock.Type.NUMBERED_LIST_ITEM) {
                String tag = t == NotionBlock.Type.BULLETED_LIST_ITEM ? "ul" : "ol";
                out.append(indent).append("<").append(tag).append(">\n");
                while (i < blocks.size() && blocks.get(i).getType() == t) {
                    NotionBlock li = blocks.get(i);
                    out.append(indent).append("  <li>").append(escape(li.getText()));
                    if (li.getChildren() != null && !li.getChildren().isEmpty()) {
                        out.append("\n");
                        renderBlocks(out, li.getChildren(), indent + "    ");
                        out.append(indent).append("  ");
                    }
                    out.append("</li>\n");
                    i++;
                }
                out.append(indent).append("</").append(tag).append(">\n");
                continue;
            }

            renderSingleBlock(out, b, indent);
            i++;
        }
    }

    private void renderSingleBlock(StringBuilder out, NotionBlock b, String indent) {
        switch (b.getType()) {
            case PARAGRAPH -> out.append(indent).append("<p>").append(escape(b.getText())).append("</p>\n");
            case HEADING_1 -> out.append(indent).append("<h2>").append(escape(b.getText())).append("</h2>\n");
            case HEADING_2 -> out.append(indent).append("<h3>").append(escape(b.getText())).append("</h3>\n");
            case HEADING_3 -> out.append(indent).append("<h4>").append(escape(b.getText())).append("</h4>\n");
            case QUOTE -> out.append(indent).append("<blockquote>").append(escape(b.getText())).append("</blockquote>\n");
            case CALLOUT -> out.append(indent).append("<aside class=\"callout\">").append(escape(b.getText())).append("</aside>\n");
            case DIVIDER -> out.append(indent).append("<hr/>\n");
            case CODE -> {
                String lang = b.getLanguage() != null ? b.getLanguage() : "";
                out.append(indent).append("<pre><code class=\"language-").append(escapeAttr(lang))
                        .append("\">").append(escape(b.getText())).append("</code></pre>\n");
            }
            case IMAGE -> {
                out.append(indent).append("<figure><img src=\"").append(escapeAttr(nullToEmpty(b.getUrl())))
                        .append("\" alt=\"").append(escapeAttr(nullToEmpty(b.getText()))).append("\"/>");
                if (b.getText() != null && !b.getText().isBlank()) {
                    out.append("<figcaption>").append(escape(b.getText())).append("</figcaption>");
                }
                out.append("</figure>\n");
            }
            case BOOKMARK -> {
                String url = nullToEmpty(b.getUrl());
                String label = b.getText() != null && !b.getText().isBlank() ? b.getText() : url;
                out.append(indent).append("<p><a href=\"").append(escapeAttr(url)).append("\" rel=\"noopener\">")
                        .append(escape(label)).append("</a></p>\n");
            }
            case UNSUPPORTED -> out.append(indent).append("<!-- unsupported block -->\n");
            default -> out.append(indent).append("<p>").append(escape(b.getText())).append("</p>\n");
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return Entities.escape(s);
    }

    private String escapeAttr(String s) {
        return escape(s).replace("\"", "&quot;");
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
