package com.cy311.omnisearch.data.client;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP client for fetching raw HTML from mcmod.cn.
 * <p>
 * Pure Java, zero MC dependency. Only uses Jsoup for HTTP connections.
 * All public methods return CompletableFuture for async-friendly usage.
 * Cookie persistence is instance-scoped (thread-safe), allowing multiple independent clients.
 */
public class McmodHttpClient {

    private static final String SEARCH_URL = "https://search.mcmod.cn/s";
    private static final String BASE_URL = "https://www.mcmod.cn";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";

    // Cross-request cookie persistence (thread-safe)
    private final Map<String, String> cookieStore = new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    /**
     * Searches mcmod.cn.
     *
     * @param query Search query (null or blank returns completed empty string)
     * @return Future of the search results HTML page
     */
    public CompletableFuture<String> search(String query) {
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return executeGet(buildSearchUrl(query));
    }

    /**
     * Fetches an item detail page from mcmod.cn.
     *
     * @param itemId Item ID (e.g. "123")
     * @return Future of the item page HTML
     */
    public CompletableFuture<String> getItemPage(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return executeGet(buildItemUrl(itemId));
    }

    /**
     * Fetches a mod detail page from mcmod.cn.
     *
     * @param modId Mod ID (e.g. "456")
     * @return Future of the mod page HTML
     */
    public CompletableFuture<String> getModPage(String modId) {
        if (modId == null || modId.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return executeGet(buildModUrl(modId));
    }

    /**
     * Submits a CAPTCHA answer to mcmod.cn.
     *
     * @param answerUrl The CAPTCHA submission URL from the challenge page
     * @param answer    The CAPTCHA answer text
     * @return Future of the response HTML (typically the page originally requested)
     */
    public CompletableFuture<String> submitCaptcha(String answerUrl, String answer) {
        if (answerUrl == null || answerUrl.isBlank() || answer == null || answer.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return executePost(answerUrl, Map.of(
            "cc_captcha_answer", answer,
            "cc_captcha_submit", "1"
        ));
    }

    // ──────────────────────────────────────────────
    // URL building (package-private for testing)
    // ──────────────────────────────────────────────

    /**
     * Builds the mcmod.cn search URL with proper query encoding.
     *
     * @param query Search query
     * @return Full search URL
     */
    static String buildSearchUrl(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return SEARCH_URL + "?key=" + encoded + "&filter=3";
    }

    /**
     * Builds the mcmod.cn item detail page URL.
     *
     * @param itemId Item ID
     * @return Full item page URL
     */
    static String buildItemUrl(String itemId) {
        return BASE_URL + "/item/" + itemId + ".html";
    }

    /**
     * Builds the mcmod.cn mod detail page URL.
     *
     * @param modId Mod ID
     * @return Full mod page URL
     */
    static String buildModUrl(String modId) {
        return BASE_URL + "/class/" + modId + ".html";
    }

    // ──────────────────────────────────────────────
    // Cookie management (package-private for testing)
    // ──────────────────────────────────────────────

    /**
     * Injects cookies into the store for testing purposes.
     *
     * @param cookies Map of cookie name-value pairs to inject
     */
    void injectCookieStore(Map<String, String> cookies) {
        if (cookies != null) {
            cookieStore.putAll(cookies);
        }
    }

    /**
     * Returns a snapshot of the current cookie store.
     *
     * @return Copy of the cookie store
     */
    Map<String, String> getCookieStore() {
        return new ConcurrentHashMap<>(cookieStore);
    }

    // ──────────────────────────────────────────────
    // Internal HTTP execution
    // ──────────────────────────────────────────────

    /**
     * Executes a GET request to the specified URL.
     */
    private CompletableFuture<String> executeGet(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Connection conn = createBaseConnection(url)
                    .method(Connection.Method.GET);
                Connection.Response response = conn.execute();
                saveCookies(response);
                return response.body();
            } catch (Exception e) {
                throw new RuntimeException("GET request failed: " + url, e);
            }
        });
    }

    /**
     * Executes a GET request with query parameters.
     */
    private CompletableFuture<String> executeGet(String url, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(url);
        if (!params.isEmpty()) {
            sb.append(url.contains("?") ? "&" : "?");
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) sb.append("&");
                sb.append(entry.getKey())
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                first = false;
            }
        }
        return executeGet(sb.toString());
    }

    /**
     * Executes a POST request with form data.
     * Does not follow redirects (important for CAPTCHA flow).
     */
    private CompletableFuture<String> executePost(String url, Map<String, String> data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Connection conn = createBaseConnection(url)
                    .data(data)
                    .method(Connection.Method.POST)
                    .followRedirects(false);
                Connection.Response response = conn.execute();
                saveCookies(response);
                return response.body();
            } catch (Exception e) {
                throw new RuntimeException("POST request failed: " + url, e);
            }
        });
    }

    /**
     * Creates a base Jsoup Connection with common configuration.
     */
    private Connection createBaseConnection(String url) {
        Connection conn = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .header("Referer", "https://www.mcmod.cn/")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .ignoreHttpErrors(true)
            .timeout(30_000);
        // Load stored cookies into the connection
        for (Map.Entry<String, String> entry : cookieStore.entrySet()) {
            conn.cookie(entry.getKey(), entry.getValue());
        }
        return conn;
    }

    /**
     * Saves cookies from a Jsoup response into the cookie store.
     */
    private void saveCookies(Connection.Response response) {
        cookieStore.putAll(response.cookies());
    }
}
