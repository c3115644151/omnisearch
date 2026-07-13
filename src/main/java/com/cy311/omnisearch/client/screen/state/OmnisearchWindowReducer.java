package com.cy311.omnisearch.client.screen.state;

import com.cy311.omnisearch.client.screen.state.SearchSessionState.BodyView;
import com.cy311.omnisearch.data.model.PendingRequest;
import com.cy311.omnisearch.data.model.SearchQuery;
import com.cy311.omnisearch.search.SearchEvent;
import com.cy311.omnisearch.search.SearchState;

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
                    .withSelectedResultIndex(-1))
                .withDetail(current.detail().resetForNewPage())
                .withWindow(current.window()
                    .withLoading(SearchState.LoadingState.LOADING)
                    .withErrorMessage(null));
            case SearchEvent.SearchResultsLoaded r -> current
                .withSearch(current.search().withResults(r.results()))
                .withWindow(current.window()
                    .withPendingRequest(null)
                    .withLoading(SearchState.LoadingState.IDLE)
                    .withErrorMessage(null));
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
