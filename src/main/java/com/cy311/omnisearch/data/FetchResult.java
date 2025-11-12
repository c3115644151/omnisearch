package com.cy311.omnisearch.data;

import java.util.List;

public class FetchResult {
    private final ItemData itemData;
    private final List<SearchResult> searchResults;

    private FetchResult(ItemData itemData, List<SearchResult> searchResults) {
        this.itemData = itemData;
        this.searchResults = searchResults;
    }

    public static FetchResult withItemData(ItemData itemData) {
        return new FetchResult(itemData, null);
    }

    public static FetchResult withSearchResults(List<SearchResult> searchResults) {
        return new FetchResult(null, searchResults);
    }

    public boolean isItemData() {
        return itemData != null;
    }

    public ItemData getItemData() {
        return itemData;
    }

    public boolean isSearchResults() {
        return searchResults != null;
    }

    public List<SearchResult> getSearchResults() {
        return searchResults;
    }
}