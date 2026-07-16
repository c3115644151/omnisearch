package com.cy311.omnisearch.client.screen.state;

import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SearchSessionState(
    BodyView currentView,
    SearchQuery query,
    List<SearchHit> results,
    int resultsScrollOffset,
    int selectedResultIndex,
    boolean draggingScrollbar,
    int currentPage,
    boolean loadingMore,
    boolean hasMore,
    @Nullable String modFilter,
    List<SearchHit> unfilteredResults,
    History history
) {
    public enum BodyView { SEARCH, RESULTS, DETAIL }

    public record Snapshot(
        BodyView currentView,
        SearchQuery query,
        List<SearchHit> results,
        int resultsScrollOffset,
        int selectedResultIndex,
        int currentPage,
        boolean hasMore,
        @Nullable String modFilter,
        List<SearchHit> unfilteredResults
    ) {}

    public static SearchSessionState initial() {
        return new SearchSessionState(BodyView.SEARCH, new SearchQuery(""), List.of(), 0, -1, false, 1, false, false, null, List.of(), new History());
    }

    public Snapshot snapshot() {
        return new Snapshot(currentView, query, results, resultsScrollOffset, selectedResultIndex, currentPage, hasMore, modFilter, unfilteredResults);
    }

    public SearchSessionState withCurrentView(BodyView currentView) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, draggingScrollbar, currentPage, loadingMore, hasMore, modFilter, unfilteredResults, history);
    }

    public SearchSessionState withQuery(SearchQuery query) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, draggingScrollbar, currentPage, loadingMore, hasMore, modFilter, unfilteredResults, history);
    }

    public SearchSessionState withResults(List<SearchHit> results) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, draggingScrollbar, currentPage, loadingMore, hasMore, modFilter, unfilteredResults, history);
    }

    public SearchSessionState withResultsScrollOffset(int resultsScrollOffset) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, draggingScrollbar, currentPage, loadingMore, hasMore, modFilter, unfilteredResults, history);
    }

    public SearchSessionState withSelectedResultIndex(int selectedResultIndex) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, draggingScrollbar, currentPage, loadingMore, hasMore, modFilter, unfilteredResults, history);
    }

    public SearchSessionState withDraggingScrollbar(boolean draggingScrollbar) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, draggingScrollbar, currentPage, loadingMore, hasMore, modFilter, unfilteredResults, history);
    }

    public SearchSessionState withCurrentPage(int currentPage) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, draggingScrollbar, currentPage, loadingMore, hasMore, modFilter, unfilteredResults, history);
    }

    public SearchSessionState withLoadingMore(boolean loadingMore) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, draggingScrollbar, currentPage, loadingMore, hasMore, modFilter, unfilteredResults, history);
    }

    public SearchSessionState withHasMore(boolean hasMore) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, draggingScrollbar, currentPage, loadingMore, hasMore, modFilter, unfilteredResults, history);
    }

    public SearchSessionState withModFilter(@Nullable String modFilter) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, draggingScrollbar, currentPage, loadingMore, hasMore, modFilter, unfilteredResults, history);
    }

    public SearchSessionState withUnfilteredResults(List<SearchHit> unfilteredResults) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, draggingScrollbar, currentPage, loadingMore, hasMore, modFilter, unfilteredResults, history);
    }

    public SearchSessionState withHistory(History history) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, draggingScrollbar, currentPage, loadingMore, hasMore, modFilter, unfilteredResults, history);
    }

    public SearchSessionState restore(Snapshot snapshot, History newHistory) {
        return new SearchSessionState(
            snapshot.currentView(),
            snapshot.query(),
            snapshot.results(),
            snapshot.resultsScrollOffset(),
            snapshot.selectedResultIndex(),
            false,
            snapshot.currentPage(),
            false,
            snapshot.hasMore(),
            snapshot.modFilter(),
            snapshot.unfilteredResults(),
            newHistory
        );
    }

    /**
     * Filters the unfiltered results by mod name and returns a new state with the filter applied.
     * Preserves hasMore so pagination continues after clearing the filter.
     */
    public SearchSessionState applyModFilter(String modName) {
        var filtered = unfilteredResults.stream()
            .filter(h -> modName.equals(h.sourceMod()))
            .toList();
        return new SearchSessionState(currentView, query, filtered, 0, -1, false, currentPage, loadingMore, hasMore, modName, unfilteredResults, history);
    }

    /**
     * Removes the mod filter and restores the unfiltered results.
     * Keeps unfilteredResults intact so mod filter can be re-applied later.
     */
    public SearchSessionState clearModFilter() {
        return new SearchSessionState(currentView, query, unfilteredResults, 0, -1, false, currentPage, loadingMore, hasMore, null, unfilteredResults, history);
    }

    public static final class History {
        private final List<Snapshot> entries;

        public History() {
            this.entries = List.of();
        }

        private History(List<Snapshot> entries) {
            this.entries = entries;
        }

        public History push(Snapshot snapshot) {
            var next = new ArrayList<Snapshot>(entries.size() + 1);
            next.addAll(entries);
            next.add(snapshot);
            return new History(Collections.unmodifiableList(next));
        }

        public PopResult pop() {
            if (entries.isEmpty()) {
                return new PopResult(null, this);
            }
            var snapshot = entries.get(entries.size() - 1);
            return new PopResult(snapshot, new History(List.copyOf(entries.subList(0, entries.size() - 1))));
        }

        public boolean canGoBack() {
            return !entries.isEmpty();
        }
    }

    public record PopResult(@Nullable Snapshot snapshot, History history) {}
}
