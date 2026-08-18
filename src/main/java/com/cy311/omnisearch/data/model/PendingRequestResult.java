package com.cy311.omnisearch.data.model;

import java.util.List;

/**
 * The resolved result of resuming a CAPTCHA-blocked request.
 */
public sealed interface PendingRequestResult permits PendingRequestResult.SearchResults, PendingRequestResult.MoreSearchResults, PendingRequestResult.DetailPage {
    record SearchResults(List<SearchHit> results, String nextPageUrl) implements PendingRequestResult {}
    record MoreSearchResults(List<SearchHit> results, String nextPageUrl) implements PendingRequestResult {}
    record DetailPage(ItemPage page) implements PendingRequestResult {}
}
