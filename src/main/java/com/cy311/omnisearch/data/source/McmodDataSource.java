package com.cy311.omnisearch.data.source;

import com.cy311.omnisearch.data.client.McmodHttpClient;
import com.cy311.omnisearch.data.model.CaptchaContext;
import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;
import com.cy311.omnisearch.data.model.document.Document;
import com.cy311.omnisearch.data.parser.McmodParser;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Data source implementation for mcmod.cn.
 * <p>
 * Combines {@link McmodHttpClient}, {@link McmodParser}, and
 * {@link McmodCaptchaHandler} to provide a unified async API for
 * searching and browsing mcmod.cn.
 * <p>
 * Uses constructor injection for testability.
 */
public class McmodDataSource implements DataSource {

    private static final String BASE_URL = "https://www.mcmod.cn";

    private final McmodHttpClient client;
    private final McmodParser parser;
    private final McmodCaptchaHandler captchaHandler;

    /**
     * Creates a McmodDataSource with default components.
     */
    public McmodDataSource() {
        this(new McmodHttpClient(), new McmodParser(), new McmodCaptchaHandler());
    }

    /**
     * Creates a McmodDataSource with the given components (dependency injection).
     *
     * @param client         HTTP client for mcmod.cn
     * @param parser         HTML parser for mcmod.cn pages
     * @param captchaHandler CAPTCHA detector and parser
     */
    McmodDataSource(McmodHttpClient client, McmodParser parser, McmodCaptchaHandler captchaHandler) {
        this.client = client;
        this.parser = parser;
        this.captchaHandler = captchaHandler;
    }

    @Override
    public CompletableFuture<List<SearchHit>> search(SearchQuery query) {
        if (query == null || query.text() == null || query.text().isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return client.search(query.text())
            .thenApply(html -> {
                if (html.isBlank()) return List.<SearchHit>of();
                checkCaptcha(html, buildSearchUrl(query.text()));
                return parser.parseSearchResults(html);
            });
    }

    @Override
    public CompletableFuture<ItemPage> getPage(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        String url = BASE_URL + "/" + pageId + ".html";

        CompletableFuture<String> htmlFuture;
        if (pageId.startsWith("item/")) {
            String itemId = pageId.substring("item/".length());
            htmlFuture = client.getItemPage(itemId);
        } else if (pageId.startsWith("class/")) {
            String modId = pageId.substring("class/".length());
            htmlFuture = client.getModPage(modId);
        } else {
            return CompletableFuture.completedFuture(null);
        }

        return htmlFuture.thenApply(html -> {
            if (html.isBlank()) return null;
            checkCaptcha(html, url);
            Document doc;
            if (pageId.startsWith("item/")) {
                doc = parser.parseItemPage(html, url);
            } else {
                doc = parser.parseModPage(html, url);
            }
            return new ItemPage(pageId, doc.title(), doc.sourceMod(), doc, url);
        });
    }

    // ──────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────

    /**
     * Checks if the HTML response is a CAPTCHA page and throws if so.
     */
    private void checkCaptcha(String html, String pageUrl) {
        if (captchaHandler.isCaptchaPage(html)) {
            CaptchaContext ctx = captchaHandler.parseCaptcha(html, pageUrl);
            if (ctx != null) {
                throw new CaptchaRequiredException(ctx);
            }
        }
    }

    private static String buildSearchUrl(String query) {
        // Minimal URL for CAPTCHA answer submission context
        return BASE_URL + "/s?key=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public String name() {
        return "mcmod";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
