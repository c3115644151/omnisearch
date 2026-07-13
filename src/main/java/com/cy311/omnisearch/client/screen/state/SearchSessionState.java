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
    History history
) {
    public enum BodyView { SEARCH, RESULTS, DETAIL }

    public record Snapshot(
        BodyView currentView,
        SearchQuery query,
        List<SearchHit> results,
        int resultsScrollOffset,
        int selectedResultIndex
    ) {}

    public static SearchSessionState initial() {
        return new SearchSessionState(BodyView.SEARCH, new SearchQuery(""), List.of(), 0, -1, new History());
    }

    public Snapshot snapshot() {
        return new Snapshot(currentView, query, results, resultsScrollOffset, selectedResultIndex);
    }

    public SearchSessionState withCurrentView(BodyView currentView) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, history);
    }

    public SearchSessionState withQuery(SearchQuery query) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, history);
    }

    public SearchSessionState withResults(List<SearchHit> results) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, history);
    }

    public SearchSessionState withResultsScrollOffset(int resultsScrollOffset) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, history);
    }

    public SearchSessionState withSelectedResultIndex(int selectedResultIndex) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, history);
    }

    public SearchSessionState withHistory(History history) {
        return new SearchSessionState(currentView, query, results, resultsScrollOffset, selectedResultIndex, history);
    }

    public SearchSessionState restore(Snapshot snapshot, History newHistory) {
        return new SearchSessionState(
            snapshot.currentView(),
            snapshot.query(),
            snapshot.results(),
            snapshot.resultsScrollOffset(),
            snapshot.selectedResultIndex(),
            newHistory
        );
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
