package com.cy311.omnisearch.client.screen;

import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.CaptchaContext;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;
import com.cy311.omnisearch.data.model.document.Document;
import com.cy311.omnisearch.data.model.document.TextNode;
import com.cy311.omnisearch.search.NavigationStack;
import com.cy311.omnisearch.search.SearchEvent;
import com.cy311.omnisearch.search.SearchReducer;
import com.cy311.omnisearch.search.SearchState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OmnisearchScreen's pure logic layer.
 * <p>
 * OmnisearchScreen reads SearchState.currentPage() to decide which widget to
 * render, and dispatches events through SearchReducer to produce new state.
 * Since Screen requires a full MC environment, these tests cover only the
 * pure logic: SearchState + SearchEvent + SearchReducer = new SearchState.
 */
class OmnisearchScreenTest {

    // ── 1. QueryChanged ──────────────────────────────────────────────

    @Test
    void queryChanged_updatesQueryAndStaysOnSearchPage() {
        var state = SearchState.initial();
        var result = SearchReducer.reduce(state, new SearchEvent.QueryChanged("Create"));
        assertEquals(new SearchQuery("Create"), result.query());
        assertEquals(SearchState.Page.SEARCH, result.currentPage());
    }

    // ── 2. SearchSubmitted ───────────────────────────────────────────

    @Test
    void searchSubmitted_switchesToResultsAndLoading() {
        var state = SearchState.initial().withQuery(new SearchQuery("Create"));
        var result = SearchReducer.reduce(state, new SearchEvent.SearchSubmitted());
        assertEquals(SearchState.Page.RESULTS, result.currentPage());
        assertEquals(SearchState.LoadingState.LOADING, result.loading());
    }

    // ── 3. SearchResultsLoaded ───────────────────────────────────────

    @Test
    void searchResultsLoaded_setsResultsAndIdle() {
        var hit1 = new SearchHit("item/1", "Naga Scale", "item", "Twilight Forest");
        var hit2 = new SearchHit("item/2", "Naga Trophy", "item", "Twilight Forest");
        var state = SearchState.initial()
            .withPage(SearchState.Page.RESULTS)
            .withLoading(SearchState.LoadingState.LOADING);
        var result = SearchReducer.reduce(
            state, new SearchEvent.SearchResultsLoaded(List.of(hit1, hit2))
        );
        assertEquals(2, result.results().size());
        assertEquals("Naga Scale", result.results().get(0).name());
        assertEquals("Naga Trophy", result.results().get(1).name());
        assertEquals(SearchState.LoadingState.IDLE, result.loading());
    }

    @Test
    void searchResultsLoaded_emptyResults() {
        var state = SearchState.initial()
            .withPage(SearchState.Page.RESULTS)
            .withLoading(SearchState.LoadingState.LOADING);
        var result = SearchReducer.reduce(
            state, new SearchEvent.SearchResultsLoaded(List.of())
        );
        assertTrue(result.results().isEmpty());
        assertEquals(SearchState.LoadingState.IDLE, result.loading());
    }

    // ── 4. ResultSelected ────────────────────────────────────────────

    @Test
    void resultSelected_validIndex_switchesToDetailAndPushesNavStack() {
        var hits = List.of(
            new SearchHit("item/a", "Alpha", "item", "ModA"),
            new SearchHit("item/b", "Beta", "item", "ModB"),
            new SearchHit("item/c", "Gamma", "item", "ModC")
        );
        var state = SearchState.initial().withResults(hits);
        var result = SearchReducer.reduce(state, new SearchEvent.ResultSelected(1));
        assertEquals(SearchState.Page.DETAIL, result.currentPage());
        assertEquals(SearchState.LoadingState.LOADING, result.loading());
        assertTrue(result.navStack().canGoBack());
    }

    // ── 5. ResultSelected 越界 ───────────────────────────────────────

    @Test
    void resultSelected_outOfBounds_throws() {
        var hits = List.of(
            new SearchHit("item/a", "Alpha", "item", "ModA"),
            new SearchHit("item/b", "Beta", "item", "ModB"),
            new SearchHit("item/c", "Gamma", "item", "ModC")
        );
        var state = SearchState.initial().withResults(hits);
        assertThrows(IndexOutOfBoundsException.class,
            () -> SearchReducer.reduce(state, new SearchEvent.ResultSelected(5)));
    }

    // ── 6. DetailLoaded ──────────────────────────────────────────────

    @Test
    void detailLoaded_setsDetailPageAndIdle() {
        var doc = new Document("Title", null, null, List.of(new TextNode("content")));
        var page = new ItemPage("item/123", "Naga Scale", "Twilight Forest", doc, "url");
        var state = SearchState.initial()
            .withPage(SearchState.Page.DETAIL)
            .withLoading(SearchState.LoadingState.LOADING);
        var result = SearchReducer.reduce(state, new SearchEvent.DetailLoaded(page));
        assertSame(page, result.detailPage());
        assertEquals(SearchState.LoadingState.IDLE, result.loading());
    }

    // ── 7. GoBack（有历史）────────────────────────────────────────────

    @Test
    void goBack_withHistory_restoresPreviousState() {
        var s0 = SearchState.initial()
            .withResults(List.of(
                new SearchHit("item/1", "Naga Scale", "item", "Twilight")
            ));
        var stateWithHistory = s0.withNavStack(new NavigationStack().push(s0));
        var detailState = stateWithHistory
            .withPage(SearchState.Page.DETAIL)
            .withLoading(SearchState.LoadingState.LOADING);

        var result = SearchReducer.reduce(detailState, new SearchEvent.GoBack());
        assertEquals(SearchState.Page.SEARCH, result.currentPage());
        assertEquals(SearchState.LoadingState.IDLE, result.loading());
        assertEquals(new SearchQuery(""), result.query());
        assertFalse(result.navStack().canGoBack());
    }

    // ── 8. GoBack（空栈）──────────────────────────────────────────────

    @Test
    void goBack_emptyStack_returnsSameState() {
        var state = SearchState.initial()
            .withPage(SearchState.Page.RESULTS);
        var result = SearchReducer.reduce(state, new SearchEvent.GoBack());
        assertSame(state, result);
    }

    // ── 9. CaptchaSolved ─────────────────────────────────────────────

    @Test
    void captchaSolved_clearsCaptchaAndSetsLoading() {
        var state = SearchState.initial()
            .withCaptcha(new CaptchaContext("url", "id", "answerUrl"))
            .withLoading(SearchState.LoadingState.CAPTCHA_REQUIRED);
        var result = SearchReducer.reduce(state, new SearchEvent.CaptchaSolved("solution"));
        assertNull(result.captcha());
        assertEquals(SearchState.LoadingState.LOADING, result.loading());
    }

    // ── 10. ErrorOccurred ────────────────────────────────────────────

    @Test
    void errorOccurred_setsErrorAndMessage() {
        var state = SearchState.initial()
            .withLoading(SearchState.LoadingState.LOADING);
        var result = SearchReducer.reduce(state, new SearchEvent.ErrorOccurred("Network error"));
        assertEquals(SearchState.LoadingState.ERROR, result.loading());
        assertEquals("Network error", result.errorMessage());
    }

    // ── 11. Dismiss ──────────────────────────────────────────────────

    @Test
    void dismiss_returnsInitialState() {
        var state = SearchState.initial()
            .withPage(SearchState.Page.DETAIL)
            .withQuery(new SearchQuery("test"))
            .withResults(List.of(new SearchHit("id", "name", "type", "mod")))
            .withErrorMessage("error");
        var result = SearchReducer.reduce(state, new SearchEvent.Dismiss());
        assertEquals(SearchState.Page.SEARCH, result.currentPage());
        assertEquals(new SearchQuery(""), result.query());
        assertTrue(result.results().isEmpty());
        assertNull(result.detailPage());
        assertEquals(SearchState.LoadingState.IDLE, result.loading());
        assertNull(result.errorMessage());
        assertNull(result.captcha());
        assertFalse(result.navStack().canGoBack());
    }

    // ── 12. LoadingState 状态流转 ────────────────────────────────────

    @Test
    void loadingState_fullLifecycle() {
        var hit = new SearchHit("item/1", "Naga Scale", "item", "Twilight");
        var s0 = SearchState.initial();
        assertEquals(SearchState.LoadingState.IDLE, s0.loading());

        var s1 = SearchReducer.reduce(s0, new SearchEvent.SearchSubmitted());
        assertEquals(SearchState.LoadingState.LOADING, s1.loading());
        assertEquals(SearchState.Page.RESULTS, s1.currentPage());

        var s2 = SearchReducer.reduce(s1, new SearchEvent.SearchResultsLoaded(List.of(hit)));
        assertEquals(SearchState.LoadingState.IDLE, s2.loading());
        assertEquals(1, s2.results().size());
        assertEquals("Naga Scale", s2.results().get(0).name());
    }
}
