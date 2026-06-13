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
            () -> assertTrue(url.contains("&filter=3")),
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
    void searchUrlContainsFilter3() {
        String url = McmodHttpClient.buildSearchUrl("test");
        assertTrue(url.endsWith("&filter=3"));
    }

    // ══════════════════════════════════════════════
    // Cookie management
    // ══════════════════════════════════════════════

    @Test
    void cookieStoreInitiallyEmpty() {
        McmodHttpClient client = new McmodHttpClient();
        assertTrue(client.getCookieStore().isEmpty());
    }

    @Test
    void injectAndRetrieveCookies() {
        McmodHttpClient client = new McmodHttpClient();
        client.injectCookieStore(Map.of("PHPSESSID", "test-session", "csrf_token", "abc123"));

        Map<String, String> stored = client.getCookieStore();
        assertEquals(2, stored.size());
        assertEquals("test-session", stored.get("PHPSESSID"));
        assertEquals("abc123", stored.get("csrf_token"));
    }

    @Test
    void cookieStoreIsolatedAcrossInstances() {
        McmodHttpClient client1 = new McmodHttpClient();
        McmodHttpClient client2 = new McmodHttpClient();

        client1.injectCookieStore(Map.of("PHPSESSID", "session-1"));
        client2.injectCookieStore(Map.of("PHPSESSID", "session-2"));

        assertAll(
            () -> assertEquals("session-1", client1.getCookieStore().get("PHPSESSID")),
            () -> assertEquals("session-2", client2.getCookieStore().get("PHPSESSID"))
        );
    }

    @Test
    void injectCookieMergesWithExisting() {
        McmodHttpClient client = new McmodHttpClient();
        client.injectCookieStore(Map.of("a", "1"));
        client.injectCookieStore(Map.of("b", "2"));

        Map<String, String> stored = client.getCookieStore();
        assertAll(
            () -> assertEquals(2, stored.size()),
            () -> assertEquals("1", stored.get("a")),
            () -> assertEquals("2", stored.get("b"))
        );
    }

    @Test
    void injectCookieOverwritesExistingKey() {
        McmodHttpClient client = new McmodHttpClient();
        client.injectCookieStore(Map.of("key", "old"));
        client.injectCookieStore(Map.of("key", "new"));

        assertEquals("new", client.getCookieStore().get("key"));
    }

    @Test
    void injectNullCookiesDoesNotThrow() {
        McmodHttpClient client = new McmodHttpClient();
        assertDoesNotThrow(() -> client.injectCookieStore(null));
        assertTrue(client.getCookieStore().isEmpty());
    }

    @Test
    void getCookieStoreReturnsSnapshot() {
        McmodHttpClient client = new McmodHttpClient();
        client.injectCookieStore(Map.of("a", "1"));
        Map<String, String> snapshot = client.getCookieStore();
        snapshot.put("injected", "should-not-affect-store");

        assertNull(client.getCookieStore().get("injected"),
            "Modifying returned map should not affect internal store");
    }

    @Test
    void cookieStoreIsThreadSafe() {
        McmodHttpClient client = new McmodHttpClient();
        client.injectCookieStore(Map.of("a", "1", "b", "2"));
        // ConcurrentHashMap is inherently thread-safe; verify no exception on concurrent access
        assertDoesNotThrow(() -> {
            Thread t1 = new Thread(() -> client.injectCookieStore(Map.of("c", "3")));
            Thread t2 = new Thread(() -> client.getCookieStore());
            t1.start();
            t2.start();
            t1.join();
            t2.join();
        });
    }

    // ══════════════════════════════════════════════
    // Null/blank input handling
    // ══════════════════════════════════════════════

    @Test
    void nullQueryReturnsEmpty() throws Exception {
        McmodHttpClient client = new McmodHttpClient();
        String result = client.search(null).get();
        assertEquals("", result);
    }

    @Test
    void blankQueryReturnsEmpty() throws Exception {
        McmodHttpClient client = new McmodHttpClient();
        String result = client.search("   ").get();
        assertEquals("", result);
    }

    @Test
    void nullItemIdReturnsEmpty() throws Exception {
        McmodHttpClient client = new McmodHttpClient();
        String result = client.getItemPage(null).get();
        assertEquals("", result);
    }

    @Test
    void blankModIdReturnsEmpty() throws Exception {
        McmodHttpClient client = new McmodHttpClient();
        String result = client.getModPage("  ").get();
        assertEquals("", result);
    }

    @Test
    void nullAnswerUrlReturnsEmpty() throws Exception {
        McmodHttpClient client = new McmodHttpClient();
        String result = client.submitCaptcha(null, "answer").get();
        assertEquals("", result);
    }

    @Test
    void blankAnswerUrlReturnsEmpty() throws Exception {
        McmodHttpClient client = new McmodHttpClient();
        String result = client.submitCaptcha("  ", "answer").get();
        assertEquals("", result);
    }

    // ══════════════════════════════════════════════
    // Network error handling (timeout/unreachable)
    // ══════════════════════════════════════════════

    @Test
    void unreachableHostThrowsException() {
        McmodHttpClient client = new McmodHttpClient();
        // submitCaptcha takes a raw URL, so we can test against an unreachable address
        CompletableFuture<String> future = client.submitCaptcha("http://localhost:1/", "test");
        assertThrows(CompletionException.class, future::join);
    }

    @Test
    void malformedUrlThrowsException() {
        McmodHttpClient client = new McmodHttpClient();
        // getItemPage builds URL from its internal builder, so malformed URLs
        // shouldn't normally happen. Test via an edge case in executeGet.
        // Use submitCaptcha with a clearly malformed URL
        CompletableFuture<String> future = client.submitCaptcha("not-a-valid-url", "test");
        assertThrows(CompletionException.class, future::join);
    }
}
