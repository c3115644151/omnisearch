package com.cy311.omnisearch.data.client;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client for fetching raw HTML from mcmod.cn.
 * <p>
 * Uses Jsoup for HTTP (same as verified MapleSugar365 fork).
 * Jsoup's native cookie management matches mcmod.cn's expectations,
 * unlike java.net.http.HttpClient which triggers 403 bot detection.
 * Cookie persistence is instance-scoped (thread-safe).
 */
public class McmodHttpClient {

    private static final String SEARCH_URL = "https://search.mcmod.cn/s";
    private static final String BASE_URL = "https://www.mcmod.cn";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";

    // Cross-request cookie persistence (thread-safe)
    private final Map<String, String> cookieStore = new HashMap<>();

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    public CompletableFuture<String> search(String query) {
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return asyncGet(() -> doGet(buildSearchUrl(query)));
    }

    public CompletableFuture<String> getItemPage(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return asyncGet(() -> doGet(buildItemUrl(itemId)));
    }

    public CompletableFuture<String> getModPage(String modId) {
        if (modId == null || modId.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return asyncGet(() -> doGet(buildModUrl(modId)));
    }

    public CompletableFuture<String> submitCaptcha(String answerUrl, String answer, Map<String, String> hiddenFields) {
        if (answerUrl == null || answerUrl.isBlank() || answer == null || answer.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return asyncGet(() -> {
            Connection conn = Jsoup.connect(answerUrl)
                    .userAgent(USER_AGENT)
                    .header("Referer", answerUrl)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .cookies(cookieStore);

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
            synchronized (cookieStore) {
                cookieStore.putAll(res.cookies());
            }
            return res.body();
        });
    }

    // ──────────────────────────────────────────────
    // URL building (package-private for testing)
    // ──────────────────────────────────────────────

    public static String buildSearchUrl(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return SEARCH_URL + "?key=" + encoded + "&filter=3";
    }

    static String buildItemUrl(String itemId) {
        return BASE_URL + "/item/" + itemId + ".html";
    }

    static String buildModUrl(String modId) {
        return BASE_URL + "/class/" + modId + ".html";
    }

    // ──────────────────────────────────────────────
    // Cookie management (package-private for testing)
    // ──────────────────────────────────────────────

    void injectCookieStore(Map<String, String> cookies) {
        if (cookies != null) {
            synchronized (cookieStore) {
                cookieStore.putAll(cookies);
            }
        }
    }

    Map<String, String> getCookieStore() {
        synchronized (cookieStore) {
            return new HashMap<>(cookieStore);
        }
    }

    // ──────────────────────────────────────────────
    // Internal HTTP execution
    // ──────────────────────────────────────────────

    /**
     * Runs a blocking HTTP call on a dedicated daemon thread.
     * <p>
     * Minecraft's environment is incompatible with ForkJoinPool.commonPool()
     * (classloader issues). We use a manually created thread instead of
     * {@link CompletableFuture#supplyAsync}.
     */
    private CompletableFuture<String> asyncGet(Callable<String> task) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        thread.setName("omnisearch-http");
        thread.setDaemon(true);
        thread.start();
        return future;
    }

    private String doGet(String url) {
        try {
            Connection.Response res = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Referer", "https://www.mcmod.cn/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .cookies(cookieStore)
                .method(Connection.Method.GET)
                .ignoreHttpErrors(true)
                .execute();
            synchronized (cookieStore) {
                cookieStore.putAll(res.cookies());
            }
            return res.body();
        } catch (Exception e) {
            throw new RuntimeException("GET request failed: " + url, e);
        }
    }
}
