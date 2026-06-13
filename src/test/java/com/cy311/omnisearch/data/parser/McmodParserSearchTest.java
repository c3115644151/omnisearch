package com.cy311.omnisearch.data.parser;

import com.cy311.omnisearch.data.model.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McmodParserSearchTest {

    private final McmodParser parser = new McmodParser();

    @Test
    void normalSearchResults() {
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head">
                  <a href="https://search.mcmod.cn/item/123.html">娜迦鳞片 - 暮色森林</a>
                </div>
              </div>
              <div class="result-item">
                <div class="head">
                  <a href="https://search.mcmod.cn/class/456.html">暮色森林 - 暮色森林</a>
                </div>
              </div>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);

        assertEquals(2, results.size());

        // Item result
        SearchHit first = results.get(0);
        assertEquals("item/123", first.id());
        assertEquals("娜迦鳞片", first.name());
        assertEquals("item", first.type());
        assertEquals("暮色森林", first.sourceMod());

        // Class (mod) result
        SearchHit second = results.get(1);
        assertEquals("class/456", second.id());
        assertEquals("暮色森林", second.name());
        assertEquals("class", second.type());
        assertEquals("暮色森林", second.sourceMod());
    }

    @Test
    void searchResultsWithEnglishNames() {
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head">
                  <a href="https://search.mcmod.cn/item/789.html">娜迦鳞片 (Naga Scale) - 暮色森林 (Twilight Forest)</a>
                </div>
              </div>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);

        assertEquals(1, results.size());
        SearchHit hit = results.get(0);
        assertEquals("item/789", hit.id());
        assertEquals("娜迦鳞片", hit.name());
        assertEquals("item", hit.type());
        assertEquals("暮色森林", hit.sourceMod());
    }

    @Test
    void emptyHtmlReturnsEmptyList() {
        List<SearchHit> results = parser.parseSearchResults("");
        assertTrue(results.isEmpty());
    }

    @Test
    void nullHtmlReturnsEmptyList() {
        List<SearchHit> results = parser.parseSearchResults(null);
        assertTrue(results.isEmpty());
    }

    @Test
    void noMatchingElementsReturnsEmptyList() {
        String html = """
            <html><body>
            <div class="other-content">
              <p>No results here</p>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);
        assertTrue(results.isEmpty());
    }

    @Test
    void partialLinkHrefFormat() {
        // Some mcmod.cn links use relative paths
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head">
                  <a href="/item/42.html">测试物品 - 测试模组</a>
                </div>
              </div>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);

        assertEquals(1, results.size());
        SearchHit hit = results.get(0);
        assertEquals("item/42", hit.id());
        assertEquals("测试物品", hit.name());
        assertEquals("item", hit.type());
        assertEquals("测试模组", hit.sourceMod());
    }

    @Test
    void textWithoutSeparator() {
        // Edge case: link text without " - " separator
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head">
                  <a href="https://search.mcmod.cn/item/1.html">单个物品名</a>
                </div>
              </div>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);

        assertEquals(1, results.size());
        SearchHit hit = results.get(0);
        assertEquals("item/1", hit.id());
        assertEquals("单个物品名", hit.name());
        assertEquals("item", hit.type());
        assertNull(hit.sourceMod());
    }
}
