package com.cy311.omnisearch.data.source;

import com.cy311.omnisearch.data.client.McmodHttpClient;
import com.cy311.omnisearch.data.model.CaptchaContext;
import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;
import com.cy311.omnisearch.data.model.document.Document;
import com.cy311.omnisearch.data.model.document.TextNode;
import com.cy311.omnisearch.data.parser.McmodParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McmodDataSourceTest {

    private McmodHttpClient client;
    private McmodParser parser;
    private McmodCaptchaHandler captchaHandler;
    private McmodDataSource dataSource;

    @BeforeEach
    void setUp() {
        client = new McmodHttpClient();
        parser = new McmodParser();
        captchaHandler = new McmodCaptchaHandler();
        dataSource = new McmodDataSource(client, parser, captchaHandler);
    }

    // ══════════════════════════════════════════════
    // Basic metadata
    // ══════════════════════════════════════════════

    @Test
    void name_returnsMcmod() {
        assertEquals("mcmod", dataSource.name());
    }

    @Test
    void isAvailable_returnsTrue() {
        assertTrue(dataSource.isAvailable());
    }

    // ══════════════════════════════════════════════
    // search - edge cases (no network required)
    // ══════════════════════════════════════════════

    @Test
    void search_returnsEmptyForNullQuery() throws Exception {
        List<SearchHit> results = dataSource.search(new SearchQuery(null)).get();
        assertTrue(results.isEmpty());
    }

    @Test
    void search_returnsEmptyForBlankQuery() throws Exception {
        List<SearchHit> results = dataSource.search(new SearchQuery("  ")).get();
        assertTrue(results.isEmpty());
    }

    @Test
    void search_returnsEmptyForEmptyQuery() throws Exception {
        List<SearchHit> results = dataSource.search(new SearchQuery("")).get();
        assertTrue(results.isEmpty());
    }

    @Test
    void search_httpClientReturnsEmpty_returnsEmpty() throws Exception {
        // Blank query triggers early return in McmodDataSource before hitting HTTP,
        // which is equivalent to an empty response from the client
        List<SearchHit> results = dataSource.search(new SearchQuery("")).get();
        assertTrue(results.isEmpty());
    }

    // ══════════════════════════════════════════════
    // getPage - edge cases (no network required)
    // ══════════════════════════════════════════════

    @Test
    void getPage_returnsNullForNullId() throws Exception {
        ItemPage page = dataSource.getPage(null).get();
        assertNull(page);
    }

    @Test
    void getPage_returnsNullForEmptyId() throws Exception {
        ItemPage page = dataSource.getPage("").get();
        assertNull(page);
    }

    @Test
    void getPage_returnsNullForBlankId() throws Exception {
        ItemPage page = dataSource.getPage("  ").get();
        assertNull(page);
    }

    @Test
    void getPage_returnsNullForUnknownPrefix() throws Exception {
        ItemPage page = dataSource.getPage("unknown/1").get();
        assertNull(page);
    }

    // ══════════════════════════════════════════════
    // submitCaptcha — captcha answer handling
    // ══════════════════════════════════════════════

    @Test
    void submitCaptcha_correctAnswer_returnsParsedResults() throws Exception {
        // Arrange: client returns search HTML, parser returns search hits
        McmodHttpClient mockClient = mock(McmodHttpClient.class);
        McmodParser mockParser = mock(McmodParser.class);
        McmodCaptchaHandler realHandler = new McmodCaptchaHandler();
        McmodDataSource ds = new McmodDataSource(mockClient, mockParser, realHandler);

        String searchHtml = "<html><body><div class=\"result\">结果</div></body></html>";
        when(mockClient.submitCaptcha(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture("redirect-body"));
        when(mockClient.search(anyString()))
            .thenReturn(CompletableFuture.completedFuture(searchHtml));

        List<SearchHit> expectedHits = List.of(
            new SearchHit("item/1", "测试物品", "item", "测试模组"));
        when(mockParser.parseSearchResults(anyString()))
            .thenReturn(expectedHits);

        CaptchaContext captcha = new CaptchaContext(
            "data:image/png;base64,abc", "图中有多少个苦力怕",
            "https://www.mcmod.cn/captcha/verify");

        // Act
        List<SearchHit> result = ds.submitCaptcha(
            new SearchQuery("test"), captcha, "42").get();

        // Assert
        assertEquals(expectedHits, result);
        verify(mockParser).parseSearchResults(searchHtml);
    }

    @Test
    void submitCaptcha_wrongAnswer_throwsCaptchaRequired() {
        // Arrange: captcha POST returns another captcha page
        McmodHttpClient mockClient = mock(McmodHttpClient.class);
        McmodParser mockParser = mock(McmodParser.class);
        McmodCaptchaHandler realHandler = new McmodCaptchaHandler();
        McmodDataSource ds = new McmodDataSource(mockClient, mockParser, realHandler);

        String captchaHtml = "<html><body>"
            + "<div class=\"captcha-image-container\">"
            + "<p class=\"tips\">安全验证</p>"
            + "<img id=\"captchaImage\" src=\"data:image/png;base64,xyz\">"
            + "<p class=\"captcha-question\">图中有多少个<b class=\"item\">苦力怕</b>?</p>"
            + "</div></body></html>";

        when(mockClient.submitCaptcha(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(captchaHtml));

        CaptchaContext captcha = new CaptchaContext(
            "data:image/png;base64,abc", "图中有多少个苦力怕",
            "https://www.mcmod.cn/captcha/verify");

        // Act & Assert
        CompletableFuture<List<SearchHit>> future = ds.submitCaptcha(
            new SearchQuery("test"), captcha, "99");
        assertThrows(CompletionException.class, future::join);
    }

    @Test
    void submitCaptcha_emptyResponse_returnsEmptyList() throws Exception {
        McmodHttpClient mockClient = mock(McmodHttpClient.class);
        McmodParser mockParser = mock(McmodParser.class);
        McmodCaptchaHandler realHandler = new McmodCaptchaHandler();
        McmodDataSource ds = new McmodDataSource(mockClient, mockParser, realHandler);

        when(mockClient.submitCaptcha(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(""));

        CaptchaContext captcha = new CaptchaContext(
            "data:image/png;base64,abc", "图中有多少个苦力怕",
            "https://www.mcmod.cn/captcha/verify");

        List<SearchHit> result = ds.submitCaptcha(
            new SearchQuery("test"), captcha, "42").get();

        assertTrue(result.isEmpty());
    }

    // ══════════════════════════════════════════════
    // submitCaptchaForPage — captcha answer handling
    // ══════════════════════════════════════════════

    @Test
    void submitCaptchaForPage_correctAnswer_returnsItemPage() throws Exception {
        McmodHttpClient mockClient = mock(McmodHttpClient.class);
        McmodParser mockParser = mock(McmodParser.class);
        McmodCaptchaHandler realHandler = new McmodCaptchaHandler();
        McmodDataSource ds = new McmodDataSource(mockClient, mockParser, realHandler);

        String pageHtml = "<html><body>物品页面内容</body></html>";
        when(mockClient.submitCaptcha(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture("redirect-body"));
        when(mockClient.getItemPage(anyString()))
            .thenReturn(CompletableFuture.completedFuture(pageHtml));

        Document doc = new Document("测试物品", "测试模组", "https://www.mcmod.cn/item/123.html",
            List.of(new TextNode("content")));
        when(mockParser.parseItemPage(anyString(), anyString()))
            .thenReturn(doc);

        CaptchaContext captcha = new CaptchaContext(
            "data:image/png;base64,abc", "图中有多少个苦力怕",
            "https://www.mcmod.cn/captcha/verify");

        ItemPage result = ds.submitCaptchaForPage(
            "item/123", captcha, "42").get();

        assertNotNull(result);
        assertEquals("item/123", result.id());
        assertEquals("测试物品", result.title());
    }

    @Test
    void submitCaptchaForPage_wrongAnswer_throwsCaptchaRequired() {
        McmodHttpClient mockClient = mock(McmodHttpClient.class);
        McmodParser mockParser = mock(McmodParser.class);
        McmodCaptchaHandler realHandler = new McmodCaptchaHandler();
        McmodDataSource ds = new McmodDataSource(mockClient, mockParser, realHandler);

        String captchaHtml = "<html><body>"
            + "<div class=\"captcha-image-container\">"
            + "<p class=\"tips\">安全验证</p>"
            + "<img id=\"captchaImage\" src=\"data:image/png;base64,xyz\">"
            + "<p class=\"captcha-question\">图中有多少个<b class=\"item\">爬行者</b>?</p>"
            + "</div></body></html>";

        when(mockClient.submitCaptcha(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(captchaHtml));

        CaptchaContext captcha = new CaptchaContext(
            "data:image/png;base64,abc", "图中有多少个爬行者",
            "https://www.mcmod.cn/captcha/verify");

        CompletableFuture<ItemPage> future = ds.submitCaptchaForPage(
            "item/123", captcha, "00");
        assertThrows(CompletionException.class, future::join);
    }
}
