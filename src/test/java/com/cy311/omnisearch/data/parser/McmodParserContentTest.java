package com.cy311.omnisearch.data.parser;

import com.cy311.omnisearch.data.model.document.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McmodParserContentTest {

    private final McmodParser parser = new McmodParser();

    // Helper: parse content HTML fragment inside a full page structure
    private List<DocNode> parseContent(String contentHtml) {
        String html = """
            <html><body>
            <div class="itemname"><h5>测试</h5></div>
            <div class="item-content common-text font14">
            %s
            </div>
            </body></html>
            """.formatted(contentHtml);
        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/test.html");
        return doc.content();
    }

    @Test
    void headingParsing() {
        List<DocNode> content = parseContent("<h1>一级标题</h1><h2>二级标题</h2><h3>三级标题</h3>");

        assertEquals(4, content.size()); // heading(1) from title + 3 from content
        HeadingNode h1 = (HeadingNode) content.get(1);
        assertEquals(1, h1.getLevel());
        assertEquals("一级标题", ((TextNode) h1.getChildren().get(0)).getText());

        HeadingNode h2 = (HeadingNode) content.get(2);
        assertEquals(2, h2.getLevel());
        assertEquals("二级标题", ((TextNode) h2.getChildren().get(0)).getText());

        HeadingNode h3 = (HeadingNode) content.get(3);
        assertEquals(3, h3.getLevel());
        assertEquals("三级标题", ((TextNode) h3.getChildren().get(0)).getText());
    }

    @Test
    void paragraphWithText() {
        List<DocNode> content = parseContent("<p>这是一段文字。</p>");

        ParagraphNode para = (ParagraphNode) content.get(1);
        assertEquals(1, para.getChildren().size());
        assertEquals("这是一段文字。", ((TextNode) para.getChildren().get(0)).getText());
    }

    @Test
    void tableParsing() {
        List<DocNode> content = parseContent("""
            <table>
              <tr><th>属性</th><th>值</th></tr>
              <tr><td>类型</td><td>材料</td></tr>
              <tr><td>稀有度</td><td>普通</td></tr>
            </table>
            """);

        TableNode table = (TableNode) content.get(1);
        assertEquals(List.of("属性", "值"), table.getHeaders());
        assertEquals(2, table.getRows().size());
    }

    @Test
    void unorderedList() {
        List<DocNode> content = parseContent("<ul><li>第一项</li><li>第二项</li></ul>");

        ListNode list = (ListNode) content.get(1);
        assertFalse(list.isOrdered());
        assertEquals(2, list.getItems().size());
    }

    @Test
    void orderedList() {
        List<DocNode> content = parseContent("<ol><li>第一步</li><li>第二步</li></ol>");

        ListNode list = (ListNode) content.get(1);
        assertTrue(list.isOrdered());
        assertEquals(2, list.getItems().size());
    }

    @Test
    void linkNode() {
        List<DocNode> content = parseContent("<p>访问<a href=\"https://example.com\">示例</a>网站</p>");

        ParagraphNode para = (ParagraphNode) content.get(1);
        LinkNode link = (LinkNode) para.getChildren().stream()
            .filter(n -> n instanceof LinkNode)
            .findFirst().orElse(null);
        assertNotNull(link);
    }

    @Test
    void imageNode() {
        List<DocNode> content = parseContent("<p><img src=\"https://example.com/img.png\" alt=\"图片\"></p>");

        ParagraphNode para = (ParagraphNode) content.get(1);
        boolean hasImage = para.getChildren().stream().anyMatch(n -> n instanceof ImageInlineNode);
        assertTrue(hasImage);
    }

    @Test
    void dividerNode() {
        List<DocNode> content = parseContent("<hr>");

        boolean hasDivider = content.stream().anyMatch(n -> n instanceof DividerNode);
        assertTrue(hasDivider);
    }

    @Test
    void boldText() {
        List<DocNode> content = parseContent("<p><b>粗体文字</b></p>");

        ParagraphNode para = (ParagraphNode) content.get(1);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals(TextStyle.BOLD, styled.getStyle());
        assertEquals("粗体文字", styled.getText());
    }

    @Test
    void strongText() {
        List<DocNode> content = parseContent("<p><strong>重要文字</strong></p>");

        ParagraphNode para = (ParagraphNode) content.get(1);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals(TextStyle.BOLD, styled.getStyle());
        assertEquals("重要文字", styled.getText());
    }

    @Test
    void italicText() {
        List<DocNode> content = parseContent("<p><i>斜体文字</i></p>");

        ParagraphNode para = (ParagraphNode) content.get(1);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals(TextStyle.ITALIC, styled.getStyle());
        assertEquals("斜体文字", styled.getText());
    }

    @Test
    void emphasizedText() {
        List<DocNode> content = parseContent("<p><em>强调文字</em></p>");

        ParagraphNode para = (ParagraphNode) content.get(1);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals(TextStyle.ITALIC, styled.getStyle());
        assertEquals("强调文字", styled.getText());
    }

    @Test
    void coloredText() {
        List<DocNode> content = parseContent("<p><span style=\"color:#FFAA00\">橙色文字</span></p>");

        ParagraphNode para = (ParagraphNode) content.get(1);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals("#FFAA00", styled.getStyle().color());
        assertTrue(styled.getStyle().bold() == false && styled.getStyle().italic() == false);
        assertEquals("橙色文字", styled.getText());
    }

    @Test
    void mixedInlineContent() {
        List<DocNode> content = parseContent("<p>普通文字<b>加粗</b><i>斜体</i>结尾</p>");

        ParagraphNode para = (ParagraphNode) content.get(1);
        // Should have at least 4 children: text, styled(bold), styled(italic), text
        assertTrue(para.getChildren().size() >= 4);

        long boldCount = para.getChildren().stream()
            .filter(n -> n instanceof StyledTextNode && ((StyledTextNode) n).getStyle().bold())
            .count();
        long italicCount = para.getChildren().stream()
            .filter(n -> n instanceof StyledTextNode && ((StyledTextNode) n).getStyle().italic())
            .count();
        assertEquals(1, boldCount);
        assertEquals(1, italicCount);
    }

    @Test
    void headingH4H5H6() {
        List<DocNode> content = parseContent("<h4>四级</h4><h5>五级</h5><h6>六级</h6>");

        assertEquals(4, content.size());
        assertEquals(4, ((HeadingNode) content.get(1)).getLevel());
        assertEquals(5, ((HeadingNode) content.get(2)).getLevel());
        assertEquals(6, ((HeadingNode) content.get(3)).getLevel());
    }

    @Test
    void emptyParagraph_skipped() {
        List<DocNode> content = parseContent("<p></p><p>有内容</p>");
        assertEquals(2, content.size()); // title heading + one paragraph
        assertInstanceOf(ParagraphNode.class, content.get(1));
    }

    @Test
    void spanWithoutStyle_isTextNode() {
        List<DocNode> content = parseContent("<p><span>无样式文本</span></p>");
        ParagraphNode para = (ParagraphNode) content.get(1);
        assertInstanceOf(TextNode.class, para.getChildren().get(0));
    }

    @Test
    void tableOnlyHeaderRow() {
        List<DocNode> content = parseContent("<table><tr><th>仅标题</th></tr></table>");
        TableNode table = (TableNode) content.get(1);
        assertEquals(List.of("仅标题"), table.getHeaders());
        assertTrue(table.getRows().isEmpty());
    }

    @Test
    void nestedList() {
        List<DocNode> content = parseContent("<ul><li>外层<li><ul><li>内层</li></ul></li></ul>");
        // Should parse without exception and produce at least some content
        ListNode list = (ListNode) content.get(1);
        assertFalse(list.getItems().isEmpty());
    }

    @Test
    void imageBlockNode() {
        List<DocNode> content = parseContent("<img src=\"https://example.com/block.png\" alt=\"块级图片\">");
        boolean hasImage = content.stream().anyMatch(n -> n instanceof ImageNode);
        assertTrue(hasImage);
    }

    @Test
    void sectionHeading_notDuplicatedInChildren() {
        // Regression test for Bug #1: SectionNode heading via childNodesCopy
        List<DocNode> content = parseContent("""
            <div>
              <h2>特性</h2>
              <p>特性描述</p>
            </div>
            """);
        SectionNode section = (SectionNode) content.get(1);
        assertEquals("特性", section.getTitle());
        // The section should contain only the paragraph, not a duplicate heading
        assertFalse(section.getChildren().stream().anyMatch(
            n -> n instanceof HeadingNode), "heading should not appear in section children");
    }

    @Test
    void nullHtml() {
        var doc = parser.parseItemPage(null, "https://www.mcmod.cn/item/0.html");
        assertTrue(doc.content().isEmpty());
    }

    @Test
    void blankHtml() {
        var doc = parser.parseItemPage("", "https://www.mcmod.cn/item/0.html");
        assertTrue(doc.content().isEmpty());
    }
}
