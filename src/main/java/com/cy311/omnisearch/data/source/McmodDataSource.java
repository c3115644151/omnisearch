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
                checkCaptcha(html, McmodHttpClient.buildSearchUrl(query.text()));
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
            } else {
                throw new RuntimeException("检测到 mcmod.cn 验证码，但解析失败，可能页面结构已更改。");
            }
        }
    }

    /**
     * Submits a CAPTCHA answer and retries the original search.
     * <p>
     * After POSTing the captcha answer (with followRedirects=false),
     * the 302 redirect's Set-Cookie is saved in the HTTP client.
     * We then retry the search with the now-valid cookies.
     */
    public CompletableFuture<List<SearchHit>> submitCaptcha(SearchQuery originalQuery, CaptchaContext captcha, String answer) {
        return client.submitCaptcha(captcha.answerUrl(), answer, captcha.hiddenFields())
            .thenCompose(html -> {
                if (html == null || html.isBlank()) return CompletableFuture.completedFuture(List.of());
                // Check if response is another captcha page (wrong answer)
                if (captchaHandler.isCaptchaPage(html)) {
                    CaptchaContext newCtx = captchaHandler.parseCaptcha(html, captcha.answerUrl());
                    if (newCtx != null) {
                        throw new CaptchaRequiredException(newCtx);
                    } else {
                        throw new RuntimeException("检测到 mcmod.cn 验证码，但解析失败，可能页面结构已更改。");
                    }
                }
                // Success: cookies are now valid, retry the search
                return client.search(originalQuery.text())
                    .thenApply(searchHtml -> {
                        if (searchHtml.isBlank()) return List.<SearchHit>of();
                        // Guard against getting captcha again
                        if (captchaHandler.isCaptchaPage(searchHtml)) {
                            CaptchaContext newCtx = captchaHandler.parseCaptcha(searchHtml, McmodHttpClient.buildSearchUrl(originalQuery.text()));
                            if (newCtx != null) {
                                throw new CaptchaRequiredException(newCtx);
                            }
                        }
                        return parser.parseSearchResults(searchHtml);
                    });
            });
    }

    /**
     * Submits a CAPTCHA answer and retries the original page request.
     */
    public CompletableFuture<ItemPage> submitCaptchaForPage(String pageId, CaptchaContext captcha, String answer) {
        String url = BASE_URL + "/" + pageId + ".html";
        return client.submitCaptcha(captcha.answerUrl(), answer, captcha.hiddenFields())
            .thenCompose(html -> {
                if (html == null || html.isBlank()) return CompletableFuture.completedFuture(null);
                if (captchaHandler.isCaptchaPage(html)) {
                    CaptchaContext newCtx = captchaHandler.parseCaptcha(html, captcha.answerUrl());
                    if (newCtx != null) {
                        throw new CaptchaRequiredException(newCtx);
                    } else {
                        throw new RuntimeException("检测到 mcmod.cn 验证码，但解析失败，可能页面结构已更改。");
                    }
                }
                // Success: cookies now valid, retry the page request
                String itemId = pageId.startsWith("item/") ? pageId.substring("item/".length()) : "";
                CompletableFuture<String> pageFuture = pageId.startsWith("item/")
                    ? client.getItemPage(itemId)
                    : client.getModPage(pageId.startsWith("class/") ? pageId.substring("class/".length()) : "");
                return pageFuture.thenApply(pageHtml -> {
                    if (pageHtml == null || pageHtml.isBlank()) return null;
                    if (captchaHandler.isCaptchaPage(pageHtml)) {
                        CaptchaContext newCtx = captchaHandler.parseCaptcha(pageHtml, url);
                        if (newCtx != null) {
                            throw new CaptchaRequiredException(newCtx);
                        }
                    }
                    Document doc;
                    if (pageId.startsWith("item/")) {
                        doc = parser.parseItemPage(pageHtml, url);
                    } else {
                        doc = parser.parseModPage(pageHtml, url);
                    }
                    return new ItemPage(pageId, doc.title(), doc.sourceMod(), doc, url);
                });
            });
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
