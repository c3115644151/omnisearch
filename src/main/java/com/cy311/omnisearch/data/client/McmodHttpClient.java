package com.cy311.omnisearch.data.client;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client for fetching raw HTML from mcmod.cn.
 * <p>
 * Uses Jsoup for HTTP (same as verified MapleSugar365 fork).
 * Jsoup's native cookie management matches mcmod.cn's expectations,
 * unlike java.net.http.HttpClient which triggers 403 bot detection.
 * Cookie persistence is instance-scoped (thread-safe via SessionCookieStore).
 * <p>
 * Implements AutoCloseable to release the RequestExecutor thread pool.
 */
public class McmodHttpClient implements AutoCloseable {

    private static final String SEARCH_URL = "https://search.mcmod.cn/s";
    private static final String BASE_URL = "https://www.mcmod.cn";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";

    // Cross-request cookie persistence (thread-safe via snapshot/merge)
    private final SessionCookieStore cookieStore = new SessionCookieStore();
    private final RequestExecutor executor = new RequestExecutor();

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    public CompletableFuture<String> search(String query) {
        return search(query, 1);
    }

    public CompletableFuture<String> search(String query, int page) {
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return executor.submit(() -> doGetWithRateLimitRetry(buildSearchUrl(query, page)));
    }

    public CompletableFuture<String> getHtml(String url) {
        if (url == null || url.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return executor.submit(() -> doGetWithRateLimitRetry(url));
    }

    public CompletableFuture<String> getItemPage(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return executor.submit(() -> doGet(buildItemUrl(itemId)));
    }

    public CompletableFuture<String> getModPage(String modId) {
        if (modId == null || modId.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return executor.submit(() -> doGet(buildModUrl(modId)));
    }

    public CompletableFuture<String> submitCaptcha(String answerUrl, String answer, Map<String, String> hiddenFields) {
        if (answerUrl == null || answerUrl.isBlank() || answer == null || answer.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return executor.submit(() -> {
            Connection conn = Jsoup.connect(answerUrl)
                .userAgent(USER_AGENT)
                .header("Referer", answerUrl)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .cookies(cookieStore.snapshot());

            // Hidden fields first, then captcha params so captcha params take precedence
            if (hiddenFields != null) {
                for (Map.Entry<String, String> entry : hiddenFields.entrySet()) {
                    conn.data(entry.getKey(), entry.getValue());
                }
            }
            conn.data("cc_captcha_answer", answer);
            conn.data("cc_captcha_submit", "1");

            Connection.Response res = conn
                .method(Connection.Method.POST)
                .followRedirects(false)
                .ignoreHttpErrors(true)
                .execute();
            cookieStore.merge(res.cookies());
            return res.body();
        });
    }

    // ──────────────────────────────────────────────
    // URL building
    // ──────────────────────────────────────────────

    public static String buildSearchUrl(String query) {
        return buildSearchUrl(query, 1);
    }

    public static String buildSearchUrl(String query, int page) {
        // Default to mcmod.cn's comprehensive search (filter=3), matching the website's
        // default search. The mold param is not sent.
        return buildSearchUrl(query, page, 3);
    }

    public static String buildSearchUrl(String query, int page, int filter) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = SEARCH_URL + "?key=" + encoded + "&filter=" + filter;
        if (page > 1) {
            url += "&page=" + page;
        }
        return url;
    }

    static String buildItemUrl(String itemId) {
        return BASE_URL + "/item/" + itemId + ".html";
    }

    static String buildModUrl(String modId) {
        return BASE_URL + "/class/" + modId + ".html";
    }

    // ──────────────────────────────────────────────
    // Image download (with session cookies)
    // ──────────────────────────────────────────────

    /**
     * Downloads raw image bytes from a URL, using the same session cookies
     * and headers as other requests. Returns null on failure.
     */
    @org.jetbrains.annotations.Nullable
    public byte[] downloadImageBytes(String url) {
        try {
            Connection.Response res = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Referer", "https://www.mcmod.cn/")
                .header("Accept", "image/avif,image/webp,image/png,image/*,*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .cookies(cookieStore.snapshot())
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .execute();
            cookieStore.merge(res.cookies());
            if (res.statusCode() >= 200 && res.statusCode() < 400) {
                return res.bodyAsBytes();
            }
            com.cy311.omnisearch.OmnisearchMod.LOGGER.warn("[McmodHttpClient] status={} for: {}", res.statusCode(), url);
            return null;
        } catch (Exception e) {
            com.cy311.omnisearch.OmnisearchMod.LOGGER.warn("[McmodHttpClient] exception {} for: {}", e.toString(), url);
            return null;
        }
    }

    // ──────────────────────────────────────────────
    // Cookie management (package-private for testing)
    // ──────────────────────────────────────────────

    void injectCookieStore(Map<String, String> cookies) {
        cookieStore.merge(cookies);
    }

    Map<String, String> getCookieStore() {
        return cookieStore.snapshot();
    }

    /**
     * Returns the underlying SessionCookieStore for advanced testing.
     */
    SessionCookieStore getCookieStoreInstance() {
        return cookieStore;
    }

    // ──────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────

    @Override
    public void close() {
        executor.close();
    }

    // ──────────────────────────────────────────────
    // Internal HTTP execution
    // ──────────────────────────────────────────────

    private String doGet(String url) {
        try {
            Connection.Response res = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Referer", "https://www.mcmod.cn/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .cookies(cookieStore.snapshot())
                .method(Connection.Method.GET)
                .ignoreHttpErrors(true)
                .execute();
            cookieStore.merge(res.cookies());
            return res.body();
        } catch (Exception e) {
            throw new RuntimeException("GET request failed: " + url, e);
        }
    }

    /**
     * Performs a GET with automatic retry on mcmod.cn's rate-limit interstitial page.
     * <p>
     * When search requests arrive in quick succession (e.g. the mod auto-loading every
     * paginated page), mcmod.cn responds with a page containing "搜索太频繁，请稍后再试"
     * (0 results, no pagination). Retrying after a short delay lets the throttle relax.
     * The delay grows with each attempt so a long throttle does not hammer the server.
     */
    private String doGetWithRateLimitRetry(String url) {
        int attempt = 0;
        while (true) {
            String html = doGet(url);
            if (attempt >= 6 || html == null || !isRateLimitedPage(html)) {
                return html;
            }
            attempt++;
            long waitMs = 500L * (1L << (attempt - 1)); // 500ms, 1s, 2s, 4s, 8s, 16s
            com.cy311.omnisearch.OmnisearchMod.LOGGER.warn(
                "[McmodHttpClient] rate-limited by mcmod.cn (attempt {}); retrying {} in {}ms",
                attempt, url, waitMs
            );
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return html;
            }
        }
    }

    /** Detects mcmod.cn's "search too frequently" interstitial page. */
    static boolean isRateLimitedPage(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        // The throttle page is short (~20KB, normal results are ~45-50KB) and carries the
        // "搜索太频繁，请稍后再试" warning. Require both to avoid mis-detecting legit pages.
        return html.length() < 40000 && html.contains("搜索太频繁");
    }
}
