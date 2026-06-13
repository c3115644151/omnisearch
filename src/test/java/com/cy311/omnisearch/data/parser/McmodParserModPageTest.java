package com.cy311.omnisearch.data.parser;

import com.cy311.omnisearch.data.model.document.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McmodParserModPageTest {

    private final McmodParser parser = new McmodParser();

    @Test
    void titleExtraction() {
        String html = """
            <html><body>
            <div class="modname"><h2>暮色森林</h2></div>
            <div class="common-nav"><a class="item" href="/class/1.html">我的世界</a><a class="item" href="/class/456.html">暮色森林</a></div>
            <div class="item-content common-text font14">
              <p>暮色森林模组介绍内容。</p>
            </div>
            </body></html>
            """;

        var doc = parser.parseModPage(html, "https://www.mcmod.cn/class/456.html");

        assertEquals("暮色森林", doc.title());
    }

    @Test
    void modPageTableParsing() {
        String html = """
            <html><body>
            <div class="modname"><h2>暮色森林</h2></div>
            <div class="common-nav"><a class="item" href="/class/1.html">我的世界</a></div>
            <div class="item-content common-text font14">
              <table>
                <tr><td>名称</td><td>暮色森林</td></tr>
                <tr><td>适用版本</td><td>1.16.5, 1.18.2</td></tr>
              </table>
            </div>
            </body></html>
            """;

        var doc = parser.parseModPage(html, "https://www.mcmod.cn/class/456.html");

        TableNode table = (TableNode) doc.content().stream()
            .filter(n -> n instanceof TableNode)
            .findFirst().orElse(null);
        assertNotNull(table);
        assertEquals(List.of("名称", "暮色森林"), table.getHeaders());
        assertEquals(1, table.getRows().size());
    }

    @Test
    void altTitleSelector() {
        // Test fallback to itemname h5 selector
        String html = """
            <html><body>
            <div class="itemname"><h5>暮色森林</h5></div>
            <div class="item-content common-text font14">
              <p>模组内容</p>
            </div>
            </body></html>
            """;

        var doc = parser.parseModPage(html, "https://www.mcmod.cn/class/456.html");

        assertEquals("暮色森林", doc.title());
    }

    @Test
    void contentWithSectionDiv() {
        String html = """
            <html><body>
            <div class="modname"><h2>暮色森林</h2></div>
            <div class="item-content common-text font14">
              <div class="section-block">
                <h2>特性</h2>
                <p>特性描述文字</p>
              </div>
            </div>
            </body></html>
            """;

        var doc = parser.parseModPage(html, "https://www.mcmod.cn/class/456.html");

        // Should contain a SectionNode
        boolean hasSection = doc.content().stream().anyMatch(n -> n instanceof SectionNode);
        assertTrue(hasSection);
    }

    @Test
    void nullHtmlSafe() {
        var doc = parser.parseModPage(null, "https://www.mcmod.cn/class/0.html");
        assertEquals("", doc.title());
        assertNull(doc.sourceMod());
        assertTrue(doc.content().isEmpty());
    }

    @Test
    void blankHtmlSafe() {
        var doc = parser.parseModPage("", "https://www.mcmod.cn/class/0.html");
        assertEquals("", doc.title());
        assertTrue(doc.content().isEmpty());
    }

    @Test
    void sourceModExtraction() {
        String html = """
            <html><body>
            <div class="modname"><h2>暮色森林</h2></div>
            <div class="common-nav">
              <a class="item" href="/class/1.html">我的世界</a>
              <a class="item" href="/class/456.html">暮色森林</a>
            </div>
            <div class="item-content common-text font14"><p>内容</p></div>
            </body></html>
            """;
        var doc = parser.parseModPage(html, "https://www.mcmod.cn/class/456.html");
        assertEquals("暮色森林", doc.sourceMod());
    }

    @Test
    void unwantedElementsRemoved() {
        String html = """
            <html><body>
            <div class="modname"><h2>暮色森林</h2></div>
            <div class="item-content common-text font14">
              <p>正文</p>
              <div class="common-text-menu"><p>目录</p></div>
              <div class="uknowtoomuch"><p>吐槽</p></div>
            </div>
            </body></html>
            """;
        var doc = parser.parseModPage(html, "https://www.mcmod.cn/class/456.html");
        long paraCount = doc.content().stream().filter(n -> n instanceof ParagraphNode).count();
        assertEquals(1, paraCount, "unwanted divs should be removed");
    }

    @Test
    void h5TitleSelector() {
        String html = """
            <html><body>
            <div class="modname"><h5>暮色森林</h5></div>
            <div class="item-content common-text font14"><p>内容</p></div>
            </body></html>
            """;
        var doc = parser.parseModPage(html, "https://www.mcmod.cn/class/456.html");
        assertEquals("暮色森林", doc.title());
    }
}
