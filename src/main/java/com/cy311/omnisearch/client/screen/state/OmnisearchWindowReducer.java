package com.cy311.omnisearch.client.screen.state;

import com.cy311.omnisearch.client.screen.state.SearchSessionState.BodyView;
import com.cy311.omnisearch.data.model.PendingRequest;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;
import com.cy311.omnisearch.search.SearchEvent;
import com.cy311.omnisearch.search.SearchState;

import java.util.ArrayList;
import java.util.List;

public final class OmnisearchWindowReducer {

    private OmnisearchWindowReducer() {}

    public static OmnisearchWindowState reduce(OmnisearchWindowState current, SearchEvent event) {
        return switch (event) {
            case SearchEvent.QueryChanged q -> current.withSearch(
                current.search().withQuery(new SearchQuery(q.query()))
            );
            case SearchEvent.SearchSubmitted ignored -> current
                .withSearch(current.search()
                    .withCurrentView(BodyView.RESULTS)
                    .withResultsScrollOffset(0)
                    .withSelectedResultIndex(-1)
                    .withCurrentPage(1)
                    .withLoadingMore(false)
                    .withHasMore(false)
                    .withNextPageUrl(null)
                    .withResults(List.of())
                    .withUnfilteredResults(List.of()))
                .withDetail(current.detail().resetForNewPage())
                .withWindow(current.window()
                    .withLoading(SearchState.LoadingState.LOADING)
                    .withErrorMessage(null));
            case SearchEvent.SearchResultsLoaded r -> {
                boolean hasMore = r.nextPageUrl() != null && !r.nextPageUrl().isBlank();
                String modFilter = current.search().modFilter();
                List<SearchHit> unfiltered = r.results();
                List<SearchHit> displayResults = unfiltered;
                if (modFilter != null && !modFilter.isBlank()) {
                    // Use contains() for looser matching — mcmod.cn's sourceMod may include
                    // extra info like "更多实用设备 (Extra Utilities)" while the resolved
                    // mod name from the registry is just "更多实用设备"
                    displayResults = unfiltered.stream()
                        .filter(h -> h.sourceMod() != null && h.sourceMod().contains(modFilter))
                        .toList();
                    // Fallback: if filter matches nothing, show all results anyway
                    if (displayResults.isEmpty()) {
                        displayResults = unfiltered;
                    }
                }
                yield current
                    .withSearch(current.search()
                        .withResults(displayResults)
                        .withUnfilteredResults(unfiltered)
                        .withCurrentPage(1)
                        .withNextPageUrl(r.nextPageUrl())
                        .withHasMore(hasMore)
                        .withLoadingMore(false))
                    .withWindow(current.window()
                        .withPendingRequest(null)
                        .withLoading(SearchState.LoadingState.IDLE)
                        .withErrorMessage(null));
            }
            case SearchEvent.MoreResultsLoaded r -> {
                var oldUnfiltered = current.search().unfilteredResults();
                var newUnfiltered = new ArrayList<SearchHit>(oldUnfiltered.size() + r.results().size());
                newUnfiltered.addAll(oldUnfiltered);
                newUnfiltered.addAll(r.results());
                String modFilter = current.search().modFilter();
                List<SearchHit> displayResults = newUnfiltered;
                if (modFilter != null && !modFilter.isBlank()) {
                    displayResults = newUnfiltered.stream()
                        .filter(h -> h.sourceMod() != null && h.sourceMod().contains(modFilter))
                        .toList();
                    if (displayResults.isEmpty()) {
                        displayResults = newUnfiltered;
                    }
                }
                boolean hasMore = r.nextPageUrl() != null && !r.nextPageUrl().isBlank();
                yield current
                    .withSearch(current.search()
                        .withResults(displayResults)
                        .withUnfilteredResults(newUnfiltered)
                        .withCurrentPage(current.search().currentPage() + 1)
                        .withNextPageUrl(r.nextPageUrl())
                        .withHasMore(hasMore)
                        .withLoadingMore(false))
                    .withWindow(current.window()
                        .withLoading(SearchState.LoadingState.IDLE)
                        .withErrorMessage(null));
            }
            case SearchEvent.ModFilterSelected m -> current.withSearch(current.search().applyModFilter(m.modName()));
            case SearchEvent.ResultSelected r -> {
                if (r.index() < 0 || r.index() >= current.search().results().size()) {
                    throw new IndexOutOfBoundsException(
                        "Result index " + r.index() + " out of bounds for results size " + current.search().results().size()
                    );
                }
                var nextSearch = current.search()
                    .withHistory(current.search().history().push(current.search().snapshot()))
                    .withCurrentView(BodyView.DETAIL)
                    .withSelectedResultIndex(r.index());
                yield current
                    .withSearch(nextSearch)
                    .withDetail(current.detail().resetForNewPage())
                    .withWindow(current.window()
                        .withLoading(SearchState.LoadingState.LOADING)
                        .withErrorMessage(null));
            }
            case SearchEvent.DetailLoaded d -> current
                .withDetail(current.detail().withPage(d.page()).withScrollOffset(0).clearLayoutCache())
                .withWindow(current.window()
                    .withPendingRequest(null)
                    .withLoading(SearchState.LoadingState.IDLE)
                    .withErrorMessage(null));
            case SearchEvent.LinkClicked ignored -> current
                .withWindow(current.window().withLoading(SearchState.LoadingState.LOADING));
            case SearchEvent.GoBack ignored -> {
                var popped = current.search().history().pop();
                if (popped.snapshot() == null) {
                    yield current;
                }
                yield current
                    .withSearch(current.search().restore(popped.snapshot(), popped.history()))
                    .withDetail(current.detail().resetForNewPage())
                    .withWindow(current.window()
                        .withLoading(SearchState.LoadingState.IDLE)
                        .withErrorMessage(null));
            }
            case SearchEvent.CaptchaSolved ignored -> current.withWindow(
                current.window()
                    .withCaptcha(null)
                    .withLoading(SearchState.LoadingState.LOADING)
                    .withErrorMessage(null)
            );
            case SearchEvent.ErrorOccurred e -> current.withWindow(
                current.window()
                    .withPendingRequest(null)
                    .withLoading(SearchState.LoadingState.ERROR)
                    .withErrorMessage(e.message())
            );
            case SearchEvent.Dismiss ignored -> OmnisearchWindowState.initial();
        };
    }

    public static OmnisearchWindowState withPendingRequest(OmnisearchWindowState current, PendingRequest pendingRequest) {
        return current.withWindow(current.window().withPendingRequest(pendingRequest));
    }
}
