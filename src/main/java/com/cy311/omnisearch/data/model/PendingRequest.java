package com.cy311.omnisearch.data.model;

/**
 * The original request that was interrupted by a CAPTCHA challenge.
 */
public sealed interface PendingRequest permits PendingRequest.Search, PendingRequest.SearchMoreUrl, PendingRequest.Detail {
    record Search(SearchQuery query) implements PendingRequest {}
    record SearchMoreUrl(SearchQuery query, String pageUrl) implements PendingRequest {}
    record Detail(String pageId) implements PendingRequest {}
}
