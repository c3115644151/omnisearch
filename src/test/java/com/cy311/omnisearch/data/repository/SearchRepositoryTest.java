package com.cy311.omnisearch.data.repository;

import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.CaptchaContext;
import com.cy311.omnisearch.data.model.PendingRequest;
import com.cy311.omnisearch.data.model.PendingRequestResult;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;
import com.cy311.omnisearch.data.model.document.DocNodeAdapterFactory;
import com.cy311.omnisearch.data.model.document.Document;
import com.cy311.omnisearch.data.model.document.TextNode;
import com.cy311.omnisearch.data.source.CaptchaCapableDataSource;
import com.cy311.omnisearch.data.source.DataSource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class SearchRepositoryTest {

    @TempDir
    Path tempDir;

    private CacheLayer cache;
    private Gson gson;

    @BeforeEach
    void setUp() {
        cache = new CacheLayer(tempDir);
        gson = new GsonBuilder()
            .registerTypeAdapterFactory(new DocNodeAdapterFactory())
            .create();
    }

    // ══════════════════════════════════════════════
    // search() - cache hit
    // ══════════════════════════════════════════════

    @Test
    void search_cacheHit_doesNotCallDataSource() throws Exception {
        var query = new SearchQuery("cached query");
        var cachedResults = List.of(
            new SearchHit("id1", "cached", "mod", "source", null)
        );

        // Pre-populate cache
        cache.putSearchResults(query, cachedResults);

        // Create mock DS that would fail if called
        var mockDS = new MockDataSource(null, null, false);
        try (var repo = new SearchRepository(cache, mockDS)) {

        var results = repo.search(query).get();

        assertEquals(cachedResults, results);
        assertFalse(mockDS.searchCalled, "DataSource.search should not be called on cache hit");
        }
    }

    // ══════════════════════════════════════════════
    // search() - cache miss → fetch DS → cache results
    // ══════════════════════════════════════════════

    @Test
    void search_cacheMiss_fetchesFromDataSourceAndCaches() throws Exception {
        var query = new SearchQuery("miss query");
        var fetchedResults = List.of(
            new SearchHit("id2", "fetched", "mod", "source", null)
        );

        var mockDS = new MockDataSource(fetchedResults, null, false);
        try (var repo = new SearchRepository(cache, mockDS)) {

        // First call: cache miss, should fetch from DS
        var results = repo.search(query).get();

        assertEquals(fetchedResults, results);
        assertTrue(mockDS.searchCalled, "DataSource.search should be called on cache miss");

        // Second call: should hit cache (verify caching)
        mockDS.searchCalled = false;
        var cachedAgain = repo.search(query).get();
        assertEquals(fetchedResults, cachedAgain);
        assertFalse(mockDS.searchCalled, "DataSource.search should not be called when cached");
        }
    }

    // ══════════════════════════════════════════════
    // search() - DS exception → stale cache fallback
    // ══════════════════════════════════════════════

    @Test
    void search_dsException_fallsBackToStaleCache() throws Exception {
        var query = new SearchQuery("stale fallback");
        var staleResults = List.of(
            new SearchHit("id1", "stale", "mod", "source", null)
        );

        // Manually write stale data
        writeStaleSearchEntry(query, staleResults);

        var mockDS = new MockDataSource(null, null, true);
        try (var repo = new SearchRepository(cache, mockDS)) {

        var results = repo.search(query).get();

        assertEquals(staleResults, results);
        }
    }

    // ══════════════════════════════════════════════
    // search() - DS exception + no stale cache → exception
    // ══════════════════════════════════════════════

    @Test
    void search_dsExceptionNoStale_throwsException() {
        var query = new SearchQuery("no fallback");

        var mockDS = new MockDataSource(null, null, true);
        try (var repo = new SearchRepository(cache, mockDS)) {

        var future = repo.search(query);

        assertThrows(CompletionException.class, future::join);
        }
    }

    // ══════════════════════════════════════════════
    // getPage() - cache hit
    // ══════════════════════════════════════════════

    @Test
    void getPage_cacheHit_doesNotCallDataSource() throws Exception {
        var pageId = "item/456";
        var cachedPage = new ItemPage(
            pageId, "Cached Page", "TestMod",
            new Document("Doc", null, null, List.of(new TextNode("content"))),
            "https://example.com/item/456"
        );

        // Pre-populate cache
        cache.putPage(pageId, cachedPage);

        var mockDS = new MockDataSource(null, null, false);
        try (var repo = new SearchRepository(cache, mockDS)) {

        var page = repo.getPage(pageId).get();

        assertEquals(cachedPage, page);
        assertFalse(mockDS.getPageCalled, "DataSource.getPage should not be called on cache hit");
        }
    }

    // ══════════════════════════════════════════════
    // getPage() - cache miss → fetch DS → cache results
    // ══════════════════════════════════════════════

    @Test
    void getPage_cacheMiss_fetchesFromDataSourceAndCaches() throws Exception {
        var pageId = "item/789";
        var fetchedPage = new ItemPage(
            pageId, "Fetched Page", "TestMod",
            new Document("Doc", null, null, List.of(new TextNode("content"))),
            "https://example.com/item/789"
        );

        var mockDS = new MockDataSource(null, fetchedPage, false);
        try (var repo = new SearchRepository(cache, mockDS)) {

        // First call: cache miss
        var page = repo.getPage(pageId).get();

        assertEquals(fetchedPage, page);
        assertTrue(mockDS.getPageCalled, "DataSource.getPage should be called on cache miss");

        // Second call: should hit cache
        mockDS.getPageCalled = false;
        var cachedAgain = repo.getPage(pageId).get();
        assertEquals(fetchedPage, cachedAgain);
        assertFalse(mockDS.getPageCalled, "DataSource.getPage should not be called when cached");
        }
    }

    @Test
    void resumeAfterCaptcha_searchRequest_returnsSearchResultsAndCaches() throws Exception {
        var query = new SearchQuery("captcha query");
        var results = List.of(new SearchHit("id2", "fetched", "mod", "source", null));
        var mockDS = new MockCaptchaDataSource(results, null);
        try (var repo = new SearchRepository(cache, mockDS)) {

        var resumed = repo.resumeAfterCaptcha(
            new PendingRequest.Search(query),
            new CaptchaContext("url", "id", "answerUrl"),
            "42"
        ).get();

        assertInstanceOf(PendingRequestResult.SearchResults.class, resumed);
        assertEquals(results, ((PendingRequestResult.SearchResults) resumed).results());
        assertEquals(results, cache.getSearchResults(query));
        assertTrue(mockDS.submitSearchCaptchaCalled);
        }
    }

    @Test
    void resumeAfterCaptcha_detailRequest_returnsDetailPageAndCaches() throws Exception {
        var pageId = "item/789";
        var page = new ItemPage(
            pageId, "Fetched Page", "TestMod",
            new Document("Doc", null, null, List.of(new TextNode("content"))),
            "https://example.com/item/789"
        );
        var mockDS = new MockCaptchaDataSource(null, page);
        try (var repo = new SearchRepository(cache, mockDS)) {

        var resumed = repo.resumeAfterCaptcha(
            new PendingRequest.Detail(pageId),
            new CaptchaContext("url", "id", "answerUrl"),
            "42"
        ).get();

        assertInstanceOf(PendingRequestResult.DetailPage.class, resumed);
        assertEquals(page, ((PendingRequestResult.DetailPage) resumed).page());
        assertEquals(page, cache.getPage(pageId));
        assertTrue(mockDS.submitPageCaptchaCalled);
        }
    }

    // ══════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════

    private void writeStaleSearchEntry(SearchQuery query, List<SearchHit> data) {
        try {
            Path staleDir = tempDir.resolve("stale").resolve("search");
            Path staleFile = staleDir.resolve("v" + CacheLayer.CACHE_VERSION + "_" + md5(query.text()) + ".json");
            Files.createDirectories(staleDir);
            var entry = new CacheEntry<>(data, System.currentTimeMillis());
            String json = gson.toJson(entry);
            Files.writeString(staleFile, json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ══════════════════════════════════════════════
    // Mock DataSource
    // ══════════════════════════════════════════════

    static class MockDataSource implements DataSource {
        protected final List<SearchHit> searchResults;
        protected final ItemPage itemPage;
        private final boolean shouldFail;
        boolean searchCalled;
        boolean getPageCalled;

        MockDataSource(List<SearchHit> searchResults, ItemPage itemPage, boolean shouldFail) {
            this.searchResults = searchResults;
            this.itemPage = itemPage;
            this.shouldFail = shouldFail;
        }

        @Override
        public CompletableFuture<List<SearchHit>> search(SearchQuery query) {
            searchCalled = true;
            if (shouldFail) {
                return CompletableFuture.failedFuture(new RuntimeException("network error"));
            }
            return CompletableFuture.completedFuture(searchResults);
        }

        @Override
        public CompletableFuture<ItemPage> getPage(String pageId) {
            getPageCalled = true;
            if (shouldFail) {
                return CompletableFuture.failedFuture(new RuntimeException("network error"));
            }
            return CompletableFuture.completedFuture(itemPage);
        }

        @Override
        public String name() {
            return "mock";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }

    static class MockCaptchaDataSource extends MockDataSource implements CaptchaCapableDataSource {
        boolean submitSearchCaptchaCalled;
        boolean submitPageCaptchaCalled;

        MockCaptchaDataSource(List<SearchHit> searchResults, ItemPage itemPage) {
            super(searchResults, itemPage, false);
        }

        @Override
        public CompletableFuture<List<SearchHit>> submitCaptcha(SearchQuery originalQuery, CaptchaContext captcha, String answer) {
            submitSearchCaptchaCalled = true;
            return CompletableFuture.completedFuture(searchResults);
        }

        @Override
        public CompletableFuture<ItemPage> submitCaptchaForPage(String pageId, CaptchaContext captcha, String answer) {
            submitPageCaptchaCalled = true;
            return CompletableFuture.completedFuture(itemPage);
        }
    }
}
