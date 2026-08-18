package com.cy311.omnisearch.data.client;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class McmodHttpClientTest {

    // ══════════════════════════════════════════════
    // URL building
    // ══════════════════════════════════════════════

    @Test
    void buildSearchUrlNormal() {
        String url = McmodHttpClient.buildSearchUrl("娜迦");
        // filter=3 is mcmod.cn's comprehensive search (website default); the mold param is omitted
        assertEquals("https://search.mcmod.cn/s?key=%E5%A8%9C%E8%BF%A6&filter=3", url);
    }

    @Test
    void buildSearchUrlAscii() {
        String url = McmodHttpClient.buildSearchUrl("diamond");
        assertEquals("https://search.mcmod.cn/s?key=diamond&filter=3", url);
    }

    @Test
    void buildSearchUrlWithSpecialChars() {
        String url = McmodHttpClient.buildSearchUrl("a&b=c d");
        assertAll(
            () -> assertTrue(url.startsWith("https://search.mcmod.cn/s?key=")),
            () -> assertTrue(url.contains("%26"), "& should be URL-encoded"),   // &
            () -> assertTrue(url.contains("%3D"), "= should be URL-encoded"),   // =
            () -> assertTrue(url.contains("+"), "space should be encoded as +")
        );
    }

    @Test
    void buildSearchUrlEmptyQuery() {
        String url = McmodHttpClient.buildSearchUrl("");
        assertEquals("https://search.mcmod.cn/s?key=&filter=3", url);
    }

    @Test
    void buildSearchUrlPage1() {
        String url = McmodHttpClient.buildSearchUrl("test", 1);
        assertEquals("https://search.mcmod.cn/s?key=test&filter=3", url);
    }

    @Test
    void buildSearchUrlPage2() {
        String url = McmodHttpClient.buildSearchUrl("test", 2);
        assertEquals("https://search.mcmod.cn/s?key=test&filter=3&page=2", url);
    }

    @Test
    void buildItemUrl() {
        String url = McmodHttpClient.buildItemUrl("123");
        assertEquals("https://www.mcmod.cn/item/123.html", url);
    }

    @Test
    void buildItemUrlWithLetters() {
        String url = McmodHttpClient.buildItemUrl("mc-456");
        assertEquals("https://www.mcmod.cn/item/mc-456.html", url);
    }

    @Test
    void buildModUrl() {
        String url = McmodHttpClient.buildModUrl("456");
        assertEquals("https://www.mcmod.cn/class/456.html", url);
    }

    @Test
    void buildModUrlUsesClassNotMod() {
        // Verified: mcmod.cn uses /class/{id}.html for mod pages, not /mod/{id}.html
        String url = McmodHttpClient.buildModUrl("789");
        assertTrue(url.contains("/class/"));
        assertFalse(url.contains("/mod/"));
    }

    @Test
    void searchUrlContainsEncodedQuery() {
        String url = McmodHttpClient.buildSearchUrl("test");
        assertTrue(url.contains("key=test"));
    }

    // ══════════════════════════════════════════════
    // Cookie management
    // ══════════════════════════════════════════════

    @Test
    void cookieStoreInitiallyEmpty() {
        try (McmodHttpClient client = new McmodHttpClient()) {
            assertTrue(client.getCookieStore().isEmpty());
        }
    }

    @Test
    void injectAndRetrieveCookies() {
        try (McmodHttpClient client = new McmodHttpClient()) {
            client.injectCookieStore(Map.of("PHPSESSID", "test-session", "csrf_token", "abc123"));

            Map<String, String> stored = client.getCookieStore();
            assertEquals(2, stored.size());
            assertEquals("test-session", stored.get("PHPSESSID"));
            assertEquals("abc123", stored.get("csrf_token"));
        }
    }

    @Test
    void cookieStoreIsolatedAcrossInstances() {
        try (McmodHttpClient client1 = new McmodHttpClient();
             McmodHttpClient client2 = new McmodHttpClient()) {
            client1.injectCookieStore(Map.of("PHPSESSID", "session-1"));
            client2.injectCookieStore(Map.of("PHPSESSID", "session-2"));

            assertAll(
                () -> assertEquals("session-1", client1.getCookieStore().get("PHPSESSID")),
                () -> assertEquals("session-2", client2.getCookieStore().get("PHPSESSID"))
            );
        }
    }

    @Test
    void injectCookieMergesWithExisting() {
        try (McmodHttpClient client = new McmodHttpClient()) {
            client.injectCookieStore(Map.of("a", "1"));
            client.injectCookieStore(Map.of("b", "2"));

            Map<String, String> stored = client.getCookieStore();
            assertAll(
                () -> assertEquals(2, stored.size()),
                () -> assertEquals("1", stored.get("a")),
                () -> assertEquals("2", stored.get("b"))
            );
        }
    }

    @Test
    void injectCookieOverwritesExistingKey() {
        try (McmodHttpClient client = new McmodHttpClient()) {
            client.injectCookieStore(Map.of("key", "old"));
            client.injectCookieStore(Map.of("key", "new"));

            assertEquals("new", client.getCookieStore().get("key"));
        }
    }

    @Test
    void injectNullCookiesDoesNotThrow() {
        try (McmodHttpClient client = new McmodHttpClient()) {
            assertDoesNotThrow(() -> client.injectCookieStore(null));
            assertTrue(client.getCookieStore().isEmpty());
        }
    }

    @Test
    void getCookieStoreReturnsSnapshot() {
        try (McmodHttpClient client = new McmodHttpClient()) {
            client.injectCookieStore(Map.of("a", "1"));
            Map<String, String> snapshot = client.getCookieStore();
            snapshot.put("injected", "should-not-affect-store");

            assertNull(client.getCookieStore().get("injected"),
                "Modifying returned map should not affect internal store");
        }
    }

    @Test
    void cookieStoreIsThreadSafe() throws Exception {
        try (McmodHttpClient client = new McmodHttpClient()) {
            client.injectCookieStore(Map.of("a", "1", "b", "2"));
            // SessionCookieStore uses ConcurrentHashMap internally; verify no exception on concurrent access
            assertDoesNotThrow(() -> {
                Thread t1 = new Thread(() -> client.injectCookieStore(Map.of("c", "3")));
                Thread t2 = new Thread(() -> client.getCookieStore());
                t1.start();
                t2.start();
                t1.join();
                t2.join();
            });
        }
    }

    // ══════════════════════════════════════════════
    // Null/blank input handling
    // ══════════════════════════════════════════════

    @Test
    void nullQueryReturnsEmpty() throws Exception {
        try (McmodHttpClient client = new McmodHttpClient()) {
            String result = client.search(null).get();
            assertEquals("", result);
        }
    }

    @Test
    void blankQueryReturnsEmpty() throws Exception {
        try (McmodHttpClient client = new McmodHttpClient()) {
            String result = client.search("   ").get();
            assertEquals("", result);
        }
    }

    @Test
    void nullItemIdReturnsEmpty() throws Exception {
        try (McmodHttpClient client = new McmodHttpClient()) {
            String result = client.getItemPage(null).get();
            assertEquals("", result);
        }
    }

    @Test
    void blankModIdReturnsEmpty() throws Exception {
        try (McmodHttpClient client = new McmodHttpClient()) {
            String result = client.getModPage("  ").get();
            assertEquals("", result);
        }
    }

    @Test
    void nullAnswerUrlReturnsEmpty() throws Exception {
        try (McmodHttpClient client = new McmodHttpClient()) {
            String result = client.submitCaptcha(null, "answer", null).get();
            assertEquals("", result);
        }
    }

    @Test
    void blankAnswerUrlReturnsEmpty() throws Exception {
        try (McmodHttpClient client = new McmodHttpClient()) {
            String result = client.submitCaptcha("  ", "answer", null).get();
            assertEquals("", result);
        }
    }

    // ══════════════════════════════════════════════
    // Network error handling (timeout/unreachable)
    // ══════════════════════════════════════════════

    @Test
    void unreachableHostThrowsException() {
        try (McmodHttpClient client = new McmodHttpClient()) {
            // submitCaptcha takes a raw URL, so we can test against an unreachable address
            CompletableFuture<String> future = client.submitCaptcha("http://localhost:1/", "test", null);
            assertThrows(CompletionException.class, future::join);
        }
    }

    @Test
    void malformedUrlThrowsException() {
        try (McmodHttpClient client = new McmodHttpClient()) {
            // getItemPage builds URL from its internal builder, so malformed URLs
            // shouldn't normally happen. Test via an edge case in executeGet.
            // Use submitCaptcha with a clearly malformed URL
            CompletableFuture<String> future = client.submitCaptcha("not-a-valid-url", "test", null);
            assertThrows(CompletionException.class, future::join);
        }
    }

    // ══════════════════════════════════════════════
    // Rate-limit page detection
    // ══════════════════════════════════════════════

    @Test
    void rateLimitedPageDetected() {
        // mcmod.cn throttles rapid pagination fetches with a short page carrying
        // "搜索太频繁，请稍后再试。" (normal results pages are ~45-50KB)
        String shortPage = "<html><body><div class=\"warning\">搜索太频繁，请稍后再试。</div></body></html>";
        assertTrue(McmodHttpClient.isRateLimitedPage(shortPage));
    }

    @Test
    void normalSearchPageNotDetectedAsRateLimited() {
        // A normal (larger) results page must never be treated as rate-limited
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("<div class=\"result-item\"><div class=\"head\"><a href=\"/item/")
              .append(i).append(".html\">测试物品</a></div></div>");
        }
        String normalPage = "<html><body><div class=\"search-result-list\">" + sb + "</div></body></html>";
        assertFalse(McmodHttpClient.isRateLimitedPage(normalPage));
    }

    @Test
    void nullOrEmptyPageNotRateLimited() {
        assertFalse(McmodHttpClient.isRateLimitedPage(null));
        assertFalse(McmodHttpClient.isRateLimitedPage(""));
        assertFalse(McmodHttpClient.isRateLimitedPage("   "));
    }
}
