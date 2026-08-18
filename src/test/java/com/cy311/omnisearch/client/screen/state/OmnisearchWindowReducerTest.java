package com.cy311.omnisearch.client.screen.state;

import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.document.Document;
import com.cy311.omnisearch.search.SearchEvent;
import com.cy311.omnisearch.search.SearchState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OmnisearchWindowReducerTest {

    @Test
    void searchSubmitted_switchesToResultsAndLoading() {
        OmnisearchWindowState state = OmnisearchWindowState.initial();

        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.QueryChanged("娜迦"));
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchSubmitted());

        assertEquals(SearchSessionState.BodyView.RESULTS, state.search().currentView());
        assertEquals("娜迦", state.search().query().text());
        assertEquals(SearchState.LoadingState.LOADING, state.window().loading());
    }

    @Test
    void resultSelected_thenGoBack_restoresPreviousResultsView() {
        OmnisearchWindowState state = OmnisearchWindowState.initial();
        List<SearchHit> results = List.of(new SearchHit("item/1", "娜迦鳞片", "item", "暮色森林", null));

        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.QueryChanged("娜迦"));
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchSubmitted());
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchResultsLoaded(results, null));
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.ResultSelected(0));

        assertEquals(SearchSessionState.BodyView.DETAIL, state.search().currentView());
        assertEquals(0, state.search().selectedResultIndex());
        assertTrue(state.search().history().canGoBack());

        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.GoBack());

        assertEquals(SearchSessionState.BodyView.RESULTS, state.search().currentView());
        assertEquals("娜迦", state.search().query().text());
        assertEquals(results, state.search().results());
        assertNull(state.detail().page());
    }

    @Test
    void detailLoaded_updatesDetailStateAndClearsLoading() {
        OmnisearchWindowState state = OmnisearchWindowState.initial();
        List<SearchHit> results = List.of(new SearchHit("item/1", "娜迦鳞片", "item", "暮色森林", null));
        ItemPage page = new ItemPage("item/1", "娜迦鳞片", "暮色森林", new Document("title", null, null, List.of()), "https://example.com");

        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchResultsLoaded(results, null));
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.ResultSelected(0));
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.DetailLoaded(page));

        assertEquals(page, state.detail().page());
        assertEquals(SearchState.LoadingState.IDLE, state.window().loading());
        assertEquals(SearchSessionState.BodyView.DETAIL, state.search().currentView());
    }

    @Test
    void errorOccurred_setsWindowErrorOnly() {
        OmnisearchWindowState state = OmnisearchWindowState.initial();

        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.ErrorOccurred("network down"));

        assertEquals(SearchState.LoadingState.ERROR, state.window().loading());
        assertEquals("network down", state.window().errorMessage());
        assertEquals(SearchSessionState.BodyView.SEARCH, state.search().currentView());
    }

    @Test
    void modFilterSelected_filtersResultsByModName() {
        OmnisearchWindowState state = OmnisearchWindowState.initial();
        List<SearchHit> results = List.of(
            new SearchHit("item/1", "巫妖", "item", "暮色森林", null),
            new SearchHit("item/2", "巫妖塔", "item", "暮色森林", null),
            new SearchHit("item/3", "猪巫妖", "item", "诡厄巫法", null)
        );

        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchResultsLoaded(results, null));
        assertEquals(3, state.search().results().size());
        assertEquals(3, state.search().unfilteredResults().size());

        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.ModFilterSelected("暮色森林"));

        assertEquals(2, state.search().results().size());
        assertEquals("暮色森林", state.search().modFilter());
        assertEquals(3, state.search().unfilteredResults().size());
        assertTrue(state.search().results().stream().allMatch(h -> "暮色森林".equals(h.sourceMod())));
    }

    @Test
    void newSearchPreservesModFilter() {
        OmnisearchWindowState state = OmnisearchWindowState.initial();
        List<SearchHit> results = List.of(
            new SearchHit("item/1", "巫妖", "item", "暮色森林", null),
            new SearchHit("item/2", "猪巫妖", "item", "诡厄巫法", null)
        );

        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchResultsLoaded(results, null));
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.ModFilterSelected("暮色森林"));
        assertEquals("暮色森林", state.search().modFilter());

        // SearchSubmitted should preserve modFilter for mod-scoped re-search
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchSubmitted());

        assertEquals("暮色森林", state.search().modFilter());
        assertTrue(state.search().results().isEmpty()); // cleared until results loaded
        assertTrue(state.search().unfilteredResults().isEmpty());
    }

    @Test
    void searchResultsLoadedWithModFilter_filtersResults() {
        OmnisearchWindowState state = OmnisearchWindowState.initial();
        // Set mod filter first
        List<SearchHit> initialResults = List.of(
            new SearchHit("item/1", "巫妖", "item", "暮色森林", null)
        );
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchResultsLoaded(initialResults, null));
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.ModFilterSelected("暮色森林"));

        // New search results come in with mixed mods
        List<SearchHit> newResults = List.of(
            new SearchHit("item/10", "巫妖塔", "item", "暮色森林", null),
            new SearchHit("item/11", "猪巫妖", "item", "诡厄巫法", null),
            new SearchHit("item/12", "巫妖王", "item", "暮色森林", null)
        );
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchResultsLoaded(newResults, null));

        // Only 暮色森林 results should be displayed
        assertEquals(2, state.search().results().size());
        assertEquals(3, state.search().unfilteredResults().size());
        assertTrue(state.search().results().stream().allMatch(h -> "暮色森林".equals(h.sourceMod())));
    }

    @Test
    void clearModFilterThenReapply_worksCorrectly() {
        OmnisearchWindowState state = OmnisearchWindowState.initial();
        List<SearchHit> results = List.of(
            new SearchHit("item/1", "巫妖", "item", "暮色森林", null),
            new SearchHit("item/2", "猪巫妖", "item", "诡厄巫法", null)
        );

        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchResultsLoaded(results, null));
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.ModFilterSelected("暮色森林"));
        assertEquals(1, state.search().results().size());

        // Clear mod filter
        state = state.withSearch(state.search().clearModFilter());
        assertNull(state.search().modFilter());
        assertEquals(2, state.search().results().size());
        assertEquals(2, state.search().unfilteredResults().size()); // unfiltered preserved

        // Re-apply a different mod filter
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.ModFilterSelected("诡厄巫法"));
        assertEquals(1, state.search().results().size());
        assertEquals("诡厄巫法", state.search().results().get(0).sourceMod());
    }

    @Test
    void moreResultsLoaded_appendsToBothResultsAndUnfiltered() {
        OmnisearchWindowState state = OmnisearchWindowState.initial();
        List<SearchHit> page1 = List.of(
            new SearchHit("item/1", "巫妖", "item", "暮色森林", null),
            new SearchHit("item/2", "猪巫妖", "item", "诡厄巫法", null)
        );
        List<SearchHit> page2 = List.of(
            new SearchHit("item/3", "巫妖塔", "item", "暮色森林", null)
        );

        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchResultsLoaded(page1, "next"));
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.MoreResultsLoaded(page2, null));

        assertEquals(3, state.search().results().size());
        assertEquals(3, state.search().unfilteredResults().size());
    }
}
