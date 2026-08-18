package com.cy311.omnisearch.data.parser;

import com.cy311.omnisearch.data.model.SearchPageBatch;
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
        assertNull(first.category());

        // Class (mod) result
        SearchHit second = results.get(1);
        assertEquals("class/456", second.id());
        assertEquals("暮色森林", second.name());
        assertEquals("class", second.type());
        assertEquals("暮色森林", second.sourceMod());
        assertNull(second.category());
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
        assertNull(hit.category());
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
        assertNull(hit.category());
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
        assertNull(hit.category());
    }

    @Test
    void categoryTagExtractedFromHeadDiv() {
        // mcmod.cn puts category tag as text before <a> in the .head div
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head">(自然生成) <a href="https://search.mcmod.cn/item/100.html">巫妖塔 (Lich Tower) - [TF] 暮色森林 (The Twilight Forest)</a></div>
              </div>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);

        assertEquals(1, results.size());
        SearchHit hit = results.get(0);
        assertEquals("item/100", hit.id());
        assertEquals("巫妖塔", hit.name());
        assertEquals("item", hit.type());
        assertEquals("[TF] 暮色森林", hit.sourceMod());
        assertEquals("自然生成", hit.category());
    }

    @Test
    void categoryTagWithEmHighlightInLink() {
        // Real mcmod.cn HTML: search keyword wrapped in <em> tags inside <a>
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head">(生物/实体) <a target="_blank" href="https://www.mcmod.cn/item/141357.html">虚空<em>巫妖</em> - [VC] 虚空工艺 (Voidscape/VoidCraft)</a></div>
              </div>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);

        assertEquals(1, results.size());
        SearchHit hit = results.get(0);
        assertEquals("item/141357", hit.id());
        assertEquals("虚空巫妖", hit.name());
        assertEquals("item", hit.type());
        assertEquals("[VC] 虚空工艺", hit.sourceMod());
        assertEquals("生物/实体", hit.category());
    }

    @Test
    void noCategoryTagWhenHeadHasNoParenPrefix() {
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head"><a href="https://search.mcmod.cn/item/200.html">测试物品 - 测试模组</a></div>
              </div>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);

        assertEquals(1, results.size());
        SearchHit hit = results.get(0);
        assertEquals("测试物品", hit.name());
        assertEquals("测试模组", hit.sourceMod());
        assertNull(hit.category());
    }

    @Test
    void linkedSourceModDoesNotBecomeSeparateSearchResult() {
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head">
                  <a href="https://www.mcmod.cn/item/141357.html">虚空<em>巫妖</em></a> -
                  <a href="https://www.mcmod.cn/class/9999.html">[VC] 虚空工艺</a>
                </div>
              </div>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);

        assertEquals(1, results.size());
        SearchHit hit = results.get(0);
        assertEquals("item/141357", hit.id());
        assertEquals("虚空巫妖", hit.name());
        assertEquals("[VC] 虚空工艺", hit.sourceMod());
    }

    @Test
    void parseSearchResults_findsPrimaryLinkOutsideHeadBlock() {
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="meta">快照信息</div>
                <div class="body">
                  <a href="https://www.mcmod.cn/item/9363.html">巫妖 - [TF] 暮色森林</a>
                </div>
              </div>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);

        assertEquals(1, results.size());
        SearchHit hit = results.get(0);
        assertEquals("item/9363", hit.id());
        assertEquals("巫妖", hit.name());
        assertEquals("[TF] 暮色森林", hit.sourceMod());
    }

    @Test
    void parseSearchResults_acceptsNonNumericTargetToken() {
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head">
                  <a href="https://www.mcmod.cn/class/twilightforest.html">暮色森林 - [TF] The Twilight Forest</a>
                </div>
              </div>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);

        assertEquals(1, results.size());
        assertEquals("class/twilightforest", results.get(0).id());
    }

    @Test
    void parseSearchPage_extractsRealNextPageUrl() {
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head"><a href="https://www.mcmod.cn/item/200.html">测试物品 - 测试模组</a></div>
              </div>
            </div>
            <div class="pagination">
              <span class="page-item active"><a href="/s?key=%E5%B7%AB%E5%A6%96&site=&filter=1&mold=1">1</a></span>
              <span class="page-item"><a href="/s?key=%E5%B7%AB%E5%A6%96&site=&filter=1&mold=1&page=2">2</a></span>
              <span class="page-item"><a href="/s?key=%E5%B7%AB%E5%A6%96&filter=0&mold=1&page=99">热门</a></span>
              <span class="page-item"><a href="/s?key=%E5%B7%AB%E5%A6%96&site=&filter=1&mold=1&page=2">下一页</a></span>
            </div>
            </body></html>
            """;

        SearchPageBatch batch = parser.parseSearchPage(
            html,
            "https://search.mcmod.cn/s?key=%E5%B7%AB%E5%A6%96&site=&filter=1&mold=1"
        );

        assertEquals(1, batch.results().size());
        assertEquals(
            "https://search.mcmod.cn/s?key=%E5%B7%AB%E5%A6%96&site=&filter=1&mold=1&page=2",
            batch.nextPageUrl()
        );
    }

    @Test
    void resultLinkWithTrackingAmpersandSuffixIsParsed() {
        // Regression: mcmod.cn can emit result links with a tracking/redirect suffix joined by
        // '&' after the id (e.g. "/item/3991.html&from=..."). The previous strict terminator
        // anchor required the id segment to end at $/?/#/, so every such href was rejected and
        // the whole result list came back blank.
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head"><a href="https://www.mcmod.cn/item/3991.html&from=search">魔法地图 - [TF] 暮色森林</a></div>
              </div>
              <div class="result-item">
                <div class="head"><a href="https://www.mcmod.cn/class/36.html&from=search">[OF] 高清修复</a></div>
              </div>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);

        assertEquals(2, results.size());
        assertEquals("item/3991", results.get(0).id());
        assertEquals("魔法地图", results.get(0).name());
        assertEquals("[TF] 暮色森林", results.get(0).sourceMod());
        assertEquals("class/36", results.get(1).id());
        assertEquals("[OF] 高清修复", results.get(1).name());
    }

    @Test
    void extractItemRefHandlesCommonHrefShapes() {
        assertArrayEquals(new String[]{"item", "3991"}, McmodParser.extractItemRef("https://www.mcmod.cn/item/3991.html"));
        assertArrayEquals(new String[]{"item", "3991"}, McmodParser.extractItemRef("https://search.mcmod.cn/item/3991.html"));
        assertArrayEquals(new String[]{"item", "3991"}, McmodParser.extractItemRef("//www.mcmod.cn/item/3991.html?x=1"));
        assertArrayEquals(new String[]{"item", "3991"}, McmodParser.extractItemRef("/item/3991"));
        assertArrayEquals(new String[]{"item", "3991"}, McmodParser.extractItemRef("https://www.mcmod.cn/item/3991.html&from=search"));
        assertArrayEquals(new String[]{"class", "twilightforest"}, McmodParser.extractItemRef("https://www.mcmod.cn/class/twilightforest.html"));
        assertArrayEquals(new String[]{"item", "141357"}, McmodParser.extractItemRef("https://www.mcmod.cn/item/141357.html#edit"));
        assertNull(McmodParser.extractItemRef(null));
        assertNull(McmodParser.extractItemRef(""));
        assertNull(McmodParser.extractItemRef("https://www.mcmod.cn/about.html"));
    }

    @Test
    void categoryIconLinkInHeadIsSkippedForPrimaryLink() {
        // Regression: real mcmod.cn renders an empty-text category icon link
        // (.class-category > a[href*='/class/category/N-1.html']) BEFORE the real result link.
        // Selecting the first link in .head picked the icon, yielding an empty name and a
        // bogus id like "class/category", which rendered every row blank.
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head">
                  <div class="class-category"><ul><li>
                    <a class="c_3" href="//www.mcmod.cn/class/category/3-1.html" target="_blank"></a>
                  </li></ul></div>
                  <a target="_blank" href="https://www.mcmod.cn/class/21747.html">Graveyard Death Mode</a>
                </div>
                <div class="body">强化了墓园模组的巫妖</div>
              </div>
              <div class="result-item">
                <div class="head">
                  <div class="class-category"><ul><li>
                    <a class="c_2" href="//www.mcmod.cn/class/category/2-1.html" target="_blank"></a>
                  </li></ul></div>
                  <a target="_blank" href="https://www.mcmod.cn/item/9364.html">娜迦 - [TF] 暮色森林</a>
                </div>
              </div>
            </div>
            </body></html>
            """;

        List<SearchHit> results = parser.parseSearchResults(html);

        assertEquals(2, results.size());
        assertEquals("class/21747", results.get(0).id());
        assertEquals("Graveyard Death Mode", results.get(0).name());
        assertEquals("class", results.get(0).type());
        assertNull(results.get(0).sourceMod());

        assertEquals("item/9364", results.get(1).id());
        assertEquals("娜迦", results.get(1).name());
        assertEquals("item", results.get(1).type());
        assertEquals("[TF] 暮色森林", results.get(1).sourceMod());
    }

    @Test
    void parseSearchPage_dataPageLinksRebuildEncodedNextUrl() {
        // Regression: real mcmod.cn (filter=3) pagination renders
        // <a class="page-link" href="?key=巫妖&filter=3&page=2" data-page="2">.
        // The raw href has unencoded Chinese and is query-only relative; using it verbatim
        // makes the server return an error page (0 results). The next URL must instead be
        // rebuilt from the properly encoded current pageUrl with the page param swapped.
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head"><a href="https://www.mcmod.cn/item/9364.html">娜迦 - [TF] 暮色森林</a></div>
              </div>
            </div>
            <div class="pagination common-pages">
              <span class="page-item active"><a class="page-link" href="?key=%E5%B7%AB%E5%A6%96&filter=3" data-page="1">1</a></span>
              <span class="page-item"><a class="page-link" href="?key=巫妖&filter=3&page=2" data-page="2">2</a></span>
              <span class="page-item"><a class="page-link" href="?key=巫妖&filter=3&page=3" data-page="3">3</a></span>
              <span class="page-item"><a class="page-link" href="?key=巫妖&filter=3&page=2" data-page="2">后页</a></span>
              <span class="page-item"><a class="page-link" href="?key=巫妖&filter=3&page=3" data-page="3">尾页</a></span>
            </div>
            </body></html>
            """;

        String page1Url = "https://search.mcmod.cn/s?key=%E5%B7%AB%E5%A6%96&filter=3";
        SearchPageBatch page1 = parser.parseSearchPage(html, page1Url);
        assertEquals("https://search.mcmod.cn/s?key=%E5%B7%AB%E5%A6%96&filter=3&page=2", page1.nextPageUrl());

        // Walking to page 2: page param is replaced, not appended
        SearchPageBatch page2 = parser.parseSearchPage(html, page1.nextPageUrl());
        assertEquals("https://search.mcmod.cn/s?key=%E5%B7%AB%E5%A6%96&filter=3&page=3", page2.nextPageUrl());
    }

    @Test
    void parseSearchPage_lastPageHasNoNextUrl() {
        // On the final page no pagination link points beyond the current page
        String html = """
            <html><body>
            <div class="search-result-list">
              <div class="result-item">
                <div class="head"><a href="https://www.mcmod.cn/item/9364.html">娜迦 - [TF] 暮色森林</a></div>
              </div>
            </div>
            <div class="pagination common-pages">
              <span class="page-item"><a class="page-link" href="?key=巫妖&filter=3" data-page="1">1</a></span>
              <span class="page-item active"><a class="page-link" href="?key=巫妖&filter=3&page=3" data-page="3">3</a></span>
            </div>
            </body></html>
            """;

        SearchPageBatch batch = parser.parseSearchPage(
            html,
            "https://search.mcmod.cn/s?key=%E5%B7%AB%E5%A6%96&filter=3&page=3"
        );
        assertNull(batch.nextPageUrl());
    }
}
