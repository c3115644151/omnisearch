package com.cy311.omnisearch.data.repository;

import com.cy311.omnisearch.data.model.*;
import com.cy311.omnisearch.data.source.DataSource;
import com.cy311.omnisearch.data.source.McmodCaptchaHandler;
import com.cy311.omnisearch.data.source.McmodDataSource;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class SearchRepository {
    private final CacheLayer cache;
    private final DataSource primarySource;
    private final McmodCaptchaHandler captchaHandler;

    public SearchRepository(CacheLayer cache, DataSource primarySource) {
        this.cache = cache;
        this.primarySource = primarySource;
        this.captchaHandler = primarySource instanceof McmodDataSource mds
            ? new McmodCaptchaHandler() : null;
    }

    public CompletableFuture<List<SearchHit>> search(SearchQuery query) {
        // 1. Check fresh cache
        var cached = cache.getSearchResults(query);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        // 2. Cache miss → fetch remote
        return primarySource.search(query)
            .thenApply(results -> {
                cache.putSearchResults(query, results);
                return results;
            })
            .exceptionally(ex -> {
                // 3. Network error → try stale cache
                var stale = cache.getSearchResultsStale(query);
                if (stale != null) return stale;
                throw new CompletionException(ex);
            });
    }

    public CompletableFuture<ItemPage> getPage(String pageId) {
        // 1. Check fresh cache
        var cached = cache.getPage(pageId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        // 2. Cache miss → fetch remote
        return primarySource.getPage(pageId)
            .thenApply(page -> {
                if (page != null) {
                    cache.putPage(pageId, page);
                }
                return page;
            })
            .exceptionally(ex -> {
                // 3. Network error → try stale cache
                var stale = cache.getPageStale(pageId);
                if (stale != null) return stale;
                throw new CompletionException(ex);
            });
    }

    /**
     * Submits a CAPTCHA answer and retries the original search.
     */
    public CompletableFuture<List<SearchHit>> submitCaptcha(SearchQuery originalQuery, CaptchaContext captcha, String answer) {
        if (primarySource instanceof McmodDataSource mds) {
            return mds.submitCaptcha(originalQuery, captcha, answer)
                .thenApply(results -> {
                    cache.putSearchResults(originalQuery, results);
                    return results;
                });
        }
        return CompletableFuture.failedFuture(new UnsupportedOperationException("CAPTCHA not supported by this data source"));
    }

    /**
     * Submits a CAPTCHA answer and retries the original page request.
     */
    public CompletableFuture<ItemPage> submitCaptchaForPage(String pageId, CaptchaContext captcha, String answer) {
        if (primarySource instanceof McmodDataSource mds) {
            return mds.submitCaptchaForPage(pageId, captcha, answer)
                .thenApply(page -> {
                    if (page != null) cache.putPage(pageId, page);
                    return page;
                });
        }
        return CompletableFuture.failedFuture(new UnsupportedOperationException("CAPTCHA not supported by this data source"));
    }
}
