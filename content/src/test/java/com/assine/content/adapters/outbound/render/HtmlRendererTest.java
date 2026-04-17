package com.assine.content.adapters.outbound.render;

import com.assine.content.domain.notion.model.NotionBlock;
import com.assine.content.domain.notion.model.NotionPage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlRendererTest {

    private final HtmlRenderer renderer = new HtmlRenderer();

    private NotionPage page(List<NotionBlock> blocks) {
        return NotionPage.builder()
                .pageId("p").databaseId("db")
                .title("Título <com> tags")
                .summary("Um resumo & detalhes")
                .blocks(blocks)
                .build();
    }

    @Test
    void producesDocumentWithLangPtBrAndUtf8() {
        String html = renderer.renderDocument(page(List.of()));
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("<html lang=\"pt-BR\">");
        assertThat(html).contains("<meta charset=\"utf-8\"/>");
        assertThat(html).endsWith("</html>\n");
    }

    @Test
    void escapesTitleAndSummaryToPreventInjection() {
        String html = renderer.renderDocument(page(List.of()));
        // < and > and & are escaped; UTF-8 accents are preserved (charset=utf-8).
        assertThat(html).contains("T\u00edtulo &lt;com&gt; tags");
        assertThat(html).contains("Um resumo &amp; detalhes");
        assertThat(html).doesNotContain("<com>");
    }

    @Test
    void rendersHeadingsAndParagraphs() {
        List<NotionBlock> blocks = List.of(
                NotionBlock.builder().type(NotionBlock.Type.HEADING_1).text("Intro").build(),
                NotionBlock.builder().type(NotionBlock.Type.PARAGRAPH).text("Olá mundo").build(),
                NotionBlock.builder().type(NotionBlock.Type.HEADING_2).text("Sub").build()
        );
        String html = renderer.renderDocument(page(blocks));
        assertThat(html).contains("<h2>Intro</h2>");
        assertThat(html).contains("<p>Ol\u00e1 mundo</p>");
        assertThat(html).contains("<h3>Sub</h3>");
    }

    @Test
    void groupsConsecutiveBulletedListItemsIntoSingleUl() {
        List<NotionBlock> blocks = List.of(
                NotionBlock.builder().type(NotionBlock.Type.BULLETED_LIST_ITEM).text("a").build(),
                NotionBlock.builder().type(NotionBlock.Type.BULLETED_LIST_ITEM).text("b").build(),
                NotionBlock.builder().type(NotionBlock.Type.PARAGRAPH).text("outro").build(),
                NotionBlock.builder().type(NotionBlock.Type.NUMBERED_LIST_ITEM).text("1").build()
        );
        String html = renderer.renderDocument(page(blocks));
        // exactly one <ul> and one <ol>
        assertThat(html.split("<ul>", -1).length - 1).isEqualTo(1);
        assertThat(html.split("</ul>", -1).length - 1).isEqualTo(1);
        assertThat(html.split("<ol>", -1).length - 1).isEqualTo(1);
        assertThat(html).contains("<li>a</li>");
        assertThat(html).contains("<li>b</li>");
        assertThat(html).contains("<li>1</li>");
    }

    @Test
    void rendersCodeBlockWithLanguageClass() {
        List<NotionBlock> blocks = List.of(
                NotionBlock.builder().type(NotionBlock.Type.CODE).language("java").text("var x = 1;").build()
        );
        String html = renderer.renderDocument(page(blocks));
        assertThat(html).contains("<pre><code class=\"language-java\">var x = 1;</code></pre>");
    }

    @Test
    void rendersImageWithAltAndFigcaption() {
        List<NotionBlock> blocks = List.of(
                NotionBlock.builder()
                        .type(NotionBlock.Type.IMAGE)
                        .url("https://s3/img.png")
                        .text("legenda")
                        .build()
        );
        String html = renderer.renderDocument(page(blocks));
        assertThat(html).contains("<img src=\"https://s3/img.png\"");
        assertThat(html).contains("<figcaption>legenda</figcaption>");
    }

    @Test
    void unsupportedBlockBecomesHtmlComment() {
        List<NotionBlock> blocks = List.of(
                NotionBlock.builder().type(NotionBlock.Type.UNSUPPORTED).build()
        );
        assertThat(renderer.renderDocument(page(blocks)))
                .contains("<!-- unsupported block -->");
    }
}
