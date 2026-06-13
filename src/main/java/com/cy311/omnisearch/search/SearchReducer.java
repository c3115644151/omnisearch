package com.cy311.omnisearch.search;

import com.cy311.omnisearch.data.model.SearchQuery;

public class SearchReducer {
    public static SearchState reduce(SearchState current, SearchEvent event) {
        return switch (event) {
            case SearchEvent.QueryChanged q -> current.withQuery(new SearchQuery(q.query()));
            case SearchEvent.SearchSubmitted s -> current
                .withPage(SearchState.Page.RESULTS)
                .withLoading(SearchState.LoadingState.LOADING);
            case SearchEvent.SearchResultsLoaded r -> current
                .withResults(r.results())
                .withLoading(SearchState.LoadingState.IDLE);
            case SearchEvent.ResultSelected r -> {
                if (r.index() < 0 || r.index() >= current.results().size()) {
                    throw new IndexOutOfBoundsException(
                        "Result index " + r.index() + " out of bounds for results size " + current.results().size()
                    );
                }
                yield current
                    .withNavStack(current.navStack().push(current))
                    .withPage(SearchState.Page.DETAIL)
                    .withLoading(SearchState.LoadingState.LOADING);
            }
            case SearchEvent.DetailLoaded d -> current
                .withDetailPage(d.page())
                .withLoading(SearchState.LoadingState.IDLE);
            case SearchEvent.LinkClicked l -> current
                .withNavStack(current.navStack().push(current))
                .withPage(SearchState.Page.DETAIL)
                .withLoading(SearchState.LoadingState.LOADING);
            case SearchEvent.GoBack g -> {
                var result = current.navStack().pop();
                if (result.state() == null) {
                    yield current; // empty stack, no-op
                }
                yield result.state()
                    .withNavStack(result.newStack());
            }
            case SearchEvent.CaptchaSolved c -> current
                .withCaptcha(null)
                .withLoading(SearchState.LoadingState.LOADING);
            case SearchEvent.ErrorOccurred e -> current
                .withLoading(SearchState.LoadingState.ERROR)
                .withErrorMessage(e.message());
            case SearchEvent.Dismiss d -> SearchState.initial();
        };
    }
}
