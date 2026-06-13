package com.cy311.omnisearch.data.parser;

import com.cy311.omnisearch.data.model.document.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McmodParserItemPageTest {

    private final McmodParser parser = new McmodParser();

    @Test
    void titleExtraction() {
        String html = """
            <html><body>
            <div class="itemname"><h5>娜迦鳞片</h5></div>
            <div class="common-nav"><a class="item" href="/class/1.html">暮色森林</a></div>
            <div class="item-content common-text font14">
              <p>娜迦鳞片是暮色森林中的一种材料。</p>
            </div>
            </body></html>
            """;

        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/123.html");

        assertEquals("娜迦鳞片", doc.title());
        assertEquals("暮色森林", doc.sourceMod());
        assertEquals("https://www.mcmod.cn/item/123.html", doc.sourceUrl());
    }

    @Test
    void sourceModExtraction() {
        String html = """
            <html><body>
            <div class="itemname"><h5>测试物品</h5></div>
            <div class="common-nav">
              <a class="item" href="/class/1.html">我的世界</a>
              <a class="item" href="/class/2.html">暮色森林</a>
            </div>
            <div class="item-content common-text font14">
              <p>内容</p>
            </div>
            </body></html>
            """;

        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/456.html");

        // Should get the last mod link
        assertEquals("暮色森林", doc.sourceMod());
    }

    @Test
    void tableParsing() {
        String html = """
            <html><body>
            <div class="itemname"><h5>测试物品</h5></div>
            <div class="common-nav"><a class="item" href="/class/1.html">模组</a></div>
            <div class="item-content common-text font14">
              <table>
                <tr><td>类型</td><td>材料</td></tr>
                <tr><td>稀有度</td><td>普通</td></tr>
                <tr><td>可堆叠</td><td>是 (64)</td></tr>
              </table>
            </div>
            </body></html>
            """;

        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/456.html");

        // Should have: heading(1) + table
        assertFalse(doc.content().isEmpty());

        // Find the table node
        TableNode table = (TableNode) doc.content().stream()
            .filter(n -> n instanceof TableNode)
            .findFirst().orElse(null);
        assertNotNull(table);
        assertEquals(List.of("类型", "材料"), table.getHeaders());
        assertEquals(2, table.getRows().size());
    }

    @Test
    void paragraphParsing() {
        String html = """
            <html><body>
            <div class="itemname"><h5>测试物品</h5></div>
            <div class="common-nav"><a class="item" href="/class/1.html">模组</a></div>
            <div class="item-content common-text font14">
              <p>第一段文字。</p>
              <p>第二段<b>粗体</b>文字。</p>
            </div>
            </body></html>
            """;

        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/456.html");

        List<ParagraphNode> paragraphs = doc.content().stream()
            .filter(n -> n instanceof ParagraphNode)
            .map(n -> (ParagraphNode) n)
            .toList();

        assertEquals(2, paragraphs.size());
    }

    @Test
    void linkParsing() {
        String html = """
            <html><body>
            <div class="itemname"><h5>测试物品</h5></div>
            <div class="common-nav"><a class="item" href="/class/1.html">模组</a></div>
            <div class="item-content common-text font14">
              <p>更多信息请访问<a href="https://example.com">示例链接</a>。</p>
            </div>
            </body></html>
            """;

        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/456.html");

        ParagraphNode para = (ParagraphNode) doc.content().stream()
            .filter(n -> n instanceof ParagraphNode)
            .findFirst().orElse(null);
        assertNotNull(para);

        boolean hasLink = para.getChildren().stream().anyMatch(n -> n instanceof LinkNode);
        assertTrue(hasLink);
    }

    @Test
    void unwantedElementsRemoved() {
        String html = """
            <html><body>
            <div class="itemname"><h5>测试物品</h5></div>
            <div class="common-nav"><a class="item" href="/class/1.html">模组</a></div>
            <div class="item-content common-text font14">
              <p>正文内容</p>
              <div class="common-text-menu"><p>目录内容</p></div>
              <div class="uknowtoomuch"><p>用户吐槽</p></div>
            </div>
            </body></html>
            """;

        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/456.html");

        // Only the paragraph "正文内容" should be in content (plus heading)
        // The common-text-menu and uknowtoomuch divs should be removed
        long paraCount = doc.content().stream().filter(n -> n instanceof ParagraphNode).count();
        assertEquals(1, paraCount);
    }

    @Test
    void emptyContentReturnsDefaultText() {
        String html = """
            <html><body>
            <div class="itemname"><h5>空物品</h5></div>
            </body></html>
            """;

        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/empty.html");

        assertEquals("空物品", doc.title());
        assertFalse(doc.content().isEmpty());
    }

    @Test
    void nullHtmlSafe() {
        var doc = parser.parseItemPage(null, "https://www.mcmod.cn/item/0.html");
        assertEquals("", doc.title());
        assertNull(doc.sourceMod());
        assertEquals("https://www.mcmod.cn/item/0.html", doc.sourceUrl());
        assertTrue(doc.content().isEmpty());
    }

    @Test
    void blankHtmlSafe() {
        var doc = parser.parseItemPage("", "https://www.mcmod.cn/item/0.html");
        assertEquals("", doc.title());
        assertTrue(doc.content().isEmpty());
    }

    @Test
    void noTitleElement_returnsEmptyTitle() {
        String html = """
            <html><body>
            <div class="item-content common-text font14"><p>内容</p></div>
            </body></html>
            """;
        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/no-title.html");
        assertEquals("", doc.title());
    }

    @Test
    void noCommonNav_sourceModIsNull() {
        String html = """
            <html><body>
            <div class="itemname"><h5>测试物品</h5></div>
            <div class="item-content common-text font14"><p>内容</p></div>
            </body></html>
            """;
        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/no-mod.html");
        assertNull(doc.sourceMod());
    }

    @Test
    void noContentDiv_returnsDefaultText() {
        String html = """
            <html><body>
            <div class="itemname"><h5>无内容</h5></div>
            </body></html>
            """;
        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/no-content.html");
        assertEquals("无内容", doc.title());
        assertFalse(doc.content().isEmpty());
    }

    @Test
    void sectionDivParsing() {
        String html = """
            <html><body>
            <div class="itemname"><h5>测试物品</h5></div>
            <div class="item-content common-text font14">
              <div>
                <h2>用途</h2>
                <p>用于合成高级装备。</p>
              </div>
            </div>
            </body></html>
            """;
        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/section.html");
        boolean hasSection = doc.content().stream().anyMatch(n -> n instanceof SectionNode);
        assertTrue(hasSection, "div with heading should produce SectionNode");
    }
}
