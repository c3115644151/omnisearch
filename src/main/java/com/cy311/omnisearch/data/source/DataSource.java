package com.cy311.omnisearch.data.source;

import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract data source interface for querying mod/item information.
 * <p>
 * Implementations combine HTTP fetching, HTML parsing, and CAPTCHA handling
 * to provide a clean asynchronous API for the search repository layer.
 * <p>
 * All network-bound methods return {@link CompletableFuture} for async composition.
 */
public interface DataSource {

    /**
     * Searches this data source for items matching the given query.
     *
     * @param query Search parameters
     * @return Future of search result list (empty list if no results)
     */
    CompletableFuture<List<SearchHit>> search(SearchQuery query);

    /**
     * Fetches a detail page by its page ID.
     * <p>
     * Page ID format supports type-prefixed identifiers:
     * <ul>
     *   <li>{@code "item/123"} → item detail page</li>
     *   <li>{@code "class/456"} → mod detail page</li>
     * </ul>
     *
     * @param pageId Page identifier (e.g. "item/123", "class/456")
     * @return Future of the parsed page, or {@code null} if the pageId is invalid
     */
    CompletableFuture<ItemPage> getPage(String pageId);

    /**
     * Returns the display name of this data source.
     *
     * @return Data source name, e.g. "mcmod"
     */
    String name();

    /**
     * Returns whether this data source is currently available.
     *
     * @return true if the data source can accept requests
     */
    boolean isAvailable();
}
