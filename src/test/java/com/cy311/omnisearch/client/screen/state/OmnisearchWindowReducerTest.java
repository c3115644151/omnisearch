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
        List<SearchHit> results = List.of(new SearchHit("item/1", "娜迦鳞片", "item", "暮色森林"));

        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.QueryChanged("娜迦"));
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchSubmitted());
        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchResultsLoaded(results));
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
        List<SearchHit> results = List.of(new SearchHit("item/1", "娜迦鳞片", "item", "暮色森林"));
        ItemPage page = new ItemPage("item/1", "娜迦鳞片", "暮色森林", new Document("title", null, null, List.of()), "https://example.com");

        state = OmnisearchWindowReducer.reduce(state, new SearchEvent.SearchResultsLoaded(results));
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
}
