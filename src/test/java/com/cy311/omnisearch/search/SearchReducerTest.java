package com.cy311.omnisearch.search;

import com.cy311.omnisearch.data.model.CaptchaContext;
import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;
import com.cy311.omnisearch.data.model.document.Document;
import com.cy311.omnisearch.data.model.document.TextNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchReducerTest {

    @Test
    void queryChanged_updatesQuery() {
        var state = SearchState.initial();
        var event = new SearchEvent.QueryChanged("娜迦");
        var result = SearchReducer.reduce(state, event);
        assertEquals(new SearchQuery("娜迦"), result.query());
    }

    @Test
    void queryChanged_doesNotChangePage() {
        var state = SearchState.initial();
        var event = new SearchEvent.QueryChanged("test");
        var result = SearchReducer.reduce(state, event);
        assertEquals(SearchState.Page.SEARCH, result.currentPage());
    }

    @Test
    void queryChanged_doesNotChangeLoading() {
        var state = SearchState.initial();
        var event = new SearchEvent.QueryChanged("test");
        var result = SearchReducer.reduce(state, event);
        assertEquals(SearchState.LoadingState.IDLE, result.loading());
    }

    @Test
    void searchSubmitted_switchesToResultsAndLoading() {
        var state = SearchState.initial().withQuery(new SearchQuery("娜迦"));
        var event = new SearchEvent.SearchSubmitted();
        var result = SearchReducer.reduce(state, event);
        assertEquals(SearchState.Page.RESULTS, result.currentPage());
        assertEquals(SearchState.LoadingState.LOADING, result.loading());
    }

    @Test
    void searchSubmitted_preservesQuery() {
        var state = SearchState.initial().withQuery(new SearchQuery("娜迦"));
        var event = new SearchEvent.SearchSubmitted();
        var result = SearchReducer.reduce(state, event);
        assertEquals(new SearchQuery("娜迦"), result.query());
    }

    @Test
    void resultSelected_validIndex_switchesToDetailAndLoading() {
        var state = SearchState.initial().withResults(List.of(
            new SearchHit("item/1", "a", "item", "mod1"),
            new SearchHit("item/2", "b", "item", "mod2")
        ));
        var event = new SearchEvent.ResultSelected(1);
        var result = SearchReducer.reduce(state, event);
        assertEquals(SearchState.Page.DETAIL, result.currentPage());
        assertEquals(SearchState.LoadingState.LOADING, result.loading());
    }

    @Test
    void resultSelected_validIndex_pushesNavStack() {
        var state = SearchState.initial().withResults(List.of(
            new SearchHit("item/1", "a", "item", "mod1")
        ));
        var event = new SearchEvent.ResultSelected(0);
        var result = SearchReducer.reduce(state, event);
        assertTrue(result.navStack().canGoBack());
    }

    @Test
    void resultSelected_zeroIndex_works() {
        var state = SearchState.initial().withResults(List.of(
            new SearchHit("item/1", "a", "item", "mod1")
        ));
        var event = new SearchEvent.ResultSelected(0);
        var result = SearchReducer.reduce(state, event);
        assertEquals(SearchState.Page.DETAIL, result.currentPage());
        assertEquals(SearchState.LoadingState.LOADING, result.loading());
    }

    @Test
    void resultSelected_negativeIndex_throws() {
        var state = SearchState.initial().withResults(List.of(
            new SearchHit("item/1", "a", "item", "mod1")
        ));
        var event = new SearchEvent.ResultSelected(-1);
        assertThrows(IndexOutOfBoundsException.class, () ->
            SearchReducer.reduce(state, event));
    }

    @Test
    void resultSelected_indexEqualToSize_throws() {
        var state = SearchState.initial().withResults(List.of(
            new SearchHit("item/1", "a", "item", "mod1")
        ));
        var event = new SearchEvent.ResultSelected(1);
        assertThrows(IndexOutOfBoundsException.class, () ->
            SearchReducer.reduce(state, event));
    }

    @Test
    void resultSelected_indexFarOutOfBounds_throws() {
        var state = SearchState.initial().withResults(List.of(
            new SearchHit("item/1", "a", "item", "mod1")
        ));
        var event = new SearchEvent.ResultSelected(999);
        assertThrows(IndexOutOfBoundsException.class, () ->
            SearchReducer.reduce(state, event));
    }

    @Test
    void resultSelected_onEmptyResults_throws() {
        var state = SearchState.initial().withResults(List.of());
        var event = new SearchEvent.ResultSelected(0);
        assertThrows(IndexOutOfBoundsException.class, () ->
            SearchReducer.reduce(state, event));
    }

    @Test
    void detailLoaded_setsDetailPageAndIdle() {
        var state = SearchState.initial()
            .withPage(SearchState.Page.DETAIL)
            .withLoading(SearchState.LoadingState.LOADING);
        var doc = new Document("Title", null, null, List.of(new TextNode("content")));
        var page = new ItemPage("item/123", "娜迦鳞片", "暮色森林", doc, "url");
        var event = new SearchEvent.DetailLoaded(page);
        var result = SearchReducer.reduce(state, event);
        assertEquals(page, result.detailPage());
        assertEquals(SearchState.LoadingState.IDLE, result.loading());
    }

    @Test
    void detailLoaded_setsDetailPageEvenWhenLoadingIdle() {
        var state = SearchState.initial()
            .withPage(SearchState.Page.DETAIL);
        var doc = new Document("Title", null, null, List.of());
        var page = new ItemPage("id", "title", "mod", doc, "url");
        var event = new SearchEvent.DetailLoaded(page);
        var result = SearchReducer.reduce(state, event);
        assertEquals(page, result.detailPage());
        assertEquals(SearchState.LoadingState.IDLE, result.loading());
    }

    @Test
    void linkClicked_switchesToDetailAndLoading() {
        var state = SearchState.initial()
            .withPage(SearchState.Page.DETAIL);
        var event = new SearchEvent.LinkClicked("https://www.mcmod.cn/item/456.html");
        var result = SearchReducer.reduce(state, event);
        assertEquals(SearchState.Page.DETAIL, result.currentPage());
        assertEquals(SearchState.LoadingState.LOADING, result.loading());
    }

    @Test
    void linkClicked_pushesNavStack() {
        var state = SearchState.initial()
            .withPage(SearchState.Page.DETAIL);
        var event = new SearchEvent.LinkClicked("https://www.mcmod.cn/item/456.html");
        var result = SearchReducer.reduce(state, event);
        assertTrue(result.navStack().canGoBack());
    }

    @Test
    void goBack_returnsPreviousState() {
        var s0 = SearchState.initial()
            .withResults(List.of(new SearchHit("item/1", "a", "item", "mod1")));
        // Build stack: push s0, then transition to DETAIL
        var stateWithHistory = s0.withNavStack(new NavigationStack().push(s0));
        var detailState = stateWithHistory
            .withPage(SearchState.Page.DETAIL)
            .withLoading(SearchState.LoadingState.LOADING);

        var result = SearchReducer.reduce(detailState, new SearchEvent.GoBack());
        assertEquals(SearchState.Page.SEARCH, result.currentPage());
        assertEquals(SearchState.LoadingState.IDLE, result.loading());
    }

    @Test
    void goBack_restoresQueryAndResults() {
        var s0 = SearchState.initial()
            .withQuery(new SearchQuery("娜迦"))
            .withResults(List.of(new SearchHit("item/1", "娜迦鳞片", "item", "暮色森林")));
        var stateWithHistory = s0.withNavStack(new NavigationStack().push(s0));
        var detailState = stateWithHistory
            .withPage(SearchState.Page.DETAIL)
            .withLoading(SearchState.LoadingState.LOADING);

        var result = SearchReducer.reduce(detailState, new SearchEvent.GoBack());
        assertEquals(new SearchQuery("娜迦"), result.query());
        assertEquals(1, result.results().size());
        assertEquals("娜迦鳞片", result.results().get(0).name());
    }

    @Test
    void goBack_onEmptyStack_returnsCurrentState() {
        var state = SearchState.initial()
            .withPage(SearchState.Page.RESULTS);
        var result = SearchReducer.reduce(state, new SearchEvent.GoBack());
        assertSame(state, result);
    }

    @Test
    void goBack_afterMultipleNavigations() {
        // Build: SEARCH -> RESULTS -> DETAIL
        var s0 = SearchState.initial();
        var s1 = s0.withNavStack(new NavigationStack().push(s0))
            .withPage(SearchState.Page.RESULTS)
            .withLoading(SearchState.LoadingState.LOADING);
        var s2 = s1.withNavStack(s1.navStack().push(s1))
            .withPage(SearchState.Page.DETAIL)
            .withLoading(SearchState.LoadingState.LOADING);

        // Go back: DETAIL -> RESULTS
        var back1 = SearchReducer.reduce(s2, new SearchEvent.GoBack());
        assertEquals(SearchState.Page.RESULTS, back1.currentPage());
        assertTrue(back1.navStack().canGoBack());

        // Go back: RESULTS -> SEARCH
        var back2 = SearchReducer.reduce(back1, new SearchEvent.GoBack());
        assertEquals(SearchState.Page.SEARCH, back2.currentPage());
        assertFalse(back2.navStack().canGoBack());

        // Go back on empty stack — no-op
        var back3 = SearchReducer.reduce(back2, new SearchEvent.GoBack());
        assertSame(back2, back3);
    }

    @Test
    void captchaSolved_clearsCaptchaAndSetsLoading() {
        var state = SearchState.initial()
            .withCaptcha(new CaptchaContext("url", "id", "answerUrl"))
            .withLoading(SearchState.LoadingState.CAPTCHA_REQUIRED);
        var event = new SearchEvent.CaptchaSolved("solution");
        var result = SearchReducer.reduce(state, event);
        assertNull(result.captcha());
        assertEquals(SearchState.LoadingState.LOADING, result.loading());
    }

    @Test
    void captchaSolved_preservesOtherState() {
        var state = SearchState.initial()
            .withPage(SearchState.Page.RESULTS)
            .withQuery(new SearchQuery("test"))
            .withResults(List.of(new SearchHit("id", "name", "type", "mod")))
            .withCaptcha(new CaptchaContext("url", "id", "answerUrl"));
        var event = new SearchEvent.CaptchaSolved("solution");
        var result = SearchReducer.reduce(state, event);
        assertEquals(SearchState.Page.RESULTS, result.currentPage());
        assertEquals(new SearchQuery("test"), result.query());
        assertEquals(1, result.results().size());
    }

    @Test
    void captchaSolved_whenNoCaptcha_stillSetsLoading() {
        var state = SearchState.initial()
            .withLoading(SearchState.LoadingState.CAPTCHA_REQUIRED);
        var event = new SearchEvent.CaptchaSolved("solution");
        var result = SearchReducer.reduce(state, event);
        assertNull(result.captcha());
        assertEquals(SearchState.LoadingState.LOADING, result.loading());
    }

    @Test
    void errorOccurred_setsErrorAndLoading() {
        var state = SearchState.initial()
            .withLoading(SearchState.LoadingState.LOADING);
        var event = new SearchEvent.ErrorOccurred("网络错误");
        var result = SearchReducer.reduce(state, event);
        assertEquals(SearchState.LoadingState.ERROR, result.loading());
        assertEquals("网络错误", result.errorMessage());
    }

    @Test
    void errorOccurred_preservesPageAndQuery() {
        var state = SearchState.initial()
            .withPage(SearchState.Page.RESULTS)
            .withQuery(new SearchQuery("娜迦"));
        var event = new SearchEvent.ErrorOccurred("timeout");
        var result = SearchReducer.reduce(state, event);
        assertEquals(SearchState.Page.RESULTS, result.currentPage());
        assertEquals(new SearchQuery("娜迦"), result.query());
    }

    @Test
    void errorOccurred_canSetEmptyMessage() {
        var state = SearchState.initial();
        var event = new SearchEvent.ErrorOccurred("");
        var result = SearchReducer.reduce(state, event);
        assertEquals("", result.errorMessage());
    }

    @Test
    void errorOccurred_overwritesPreviousError() {
        var state = SearchState.initial().withErrorMessage("previous error");
        var event = new SearchEvent.ErrorOccurred("new error");
        var result = SearchReducer.reduce(state, event);
        assertEquals("new error", result.errorMessage());
    }

    @Test
    void dismiss_returnsInitialState() {
        var state = SearchState.initial()
            .withPage(SearchState.Page.DETAIL)
            .withQuery(new SearchQuery("test"))
            .withResults(List.of(new SearchHit("id", "name", "type", "mod")))
            .withErrorMessage("error");
        var event = new SearchEvent.Dismiss();
        var result = SearchReducer.reduce(state, event);
        assertEquals(SearchState.Page.SEARCH, result.currentPage());
        assertEquals(new SearchQuery(""), result.query());
        assertTrue(result.results().isEmpty());
        assertNull(result.detailPage());
        assertEquals(SearchState.LoadingState.IDLE, result.loading());
        assertNull(result.errorMessage());
        assertNull(result.captcha());
        assertNotNull(result.navStack());
        assertFalse(result.navStack().canGoBack());
    }

    @Test
    void dismiss_onInitialState_returnsInitialState() {
        var state = SearchState.initial();
        var event = new SearchEvent.Dismiss();
        var result = SearchReducer.reduce(state, event);
        assertEquals(SearchState.Page.SEARCH, result.currentPage());
        assertEquals(new SearchQuery(""), result.query());
        assertTrue(result.results().isEmpty());
        assertNull(result.detailPage());
        assertEquals(SearchState.LoadingState.IDLE, result.loading());
        assertNull(result.errorMessage());
        assertNull(result.captcha());
        assertNotNull(result.navStack());
        assertFalse(result.navStack().canGoBack());
    }

    @Test
    void reducer_isPure_noSideEffectsOnInput() {
        var state = SearchState.initial()
            .withQuery(new SearchQuery("original"))
            .withLoading(SearchState.LoadingState.LOADING);
        var event = new SearchEvent.QueryChanged("changed");

        var navBefore = state.navStack();
        SearchReducer.reduce(state, event);

        // State must be unchanged
        assertEquals(new SearchQuery("original"), state.query());
        assertSame(navBefore, state.navStack());
        assertEquals(SearchState.LoadingState.LOADING, state.loading());
    }

    @Test
    void reducer_isPure_resultSelected_doesNotMutateInput() {
        var state = SearchState.initial()
            .withResults(List.of(
                new SearchHit("item/1", "a", "item", "mod1"),
                new SearchHit("item/2", "b", "item", "mod2")
            ));
        var navBefore = state.navStack();
        var navCanGoBackBefore = state.navStack().canGoBack();

        var result = SearchReducer.reduce(state, new SearchEvent.ResultSelected(1));

        // Input state must be unchanged
        assertSame(navBefore, state.navStack());
        assertEquals(navCanGoBackBefore, state.navStack().canGoBack());
        assertEquals(SearchState.Page.SEARCH, state.currentPage());
        assertNull(state.detailPage());

        // Result has new navStack
        assertNotSame(navBefore, result.navStack());
        assertTrue(result.navStack().canGoBack());
    }

    @Test
    void reducer_isPure_linkClicked_doesNotMutateInput() {
        var state = SearchState.initial()
            .withPage(SearchState.Page.DETAIL);
        var navBefore = state.navStack();
        var navCanGoBackBefore = state.navStack().canGoBack();

        var result = SearchReducer.reduce(state, new SearchEvent.LinkClicked("https://example.com"));

        // Input state must be unchanged
        assertSame(navBefore, state.navStack());
        assertEquals(navCanGoBackBefore, state.navStack().canGoBack());

        // Result has new navStack
        assertNotSame(navBefore, result.navStack());
        assertTrue(result.navStack().canGoBack());
    }

    @Test
    void reducer_isPure_goBack_doesNotMutateInput() {
        // Build stack by creating a state with history
        var s0 = SearchState.initial()
            .withResults(List.of(new SearchHit("item/1", "a", "item", "mod1")));
        var detailState = s0
            .withNavStack(new NavigationStack().push(s0))
            .withPage(SearchState.Page.DETAIL)
            .withLoading(SearchState.LoadingState.LOADING);
        var navBefore = detailState.navStack();

        SearchReducer.reduce(detailState, new SearchEvent.GoBack());

        // Input navigation stack must be unchanged
        assertEquals(navBefore.canGoBack(), detailState.navStack().canGoBack());
    }

    @Test
    void reduceEvent_orderIndependent() {
        var state = SearchState.initial();
        var s1 = SearchReducer.reduce(state, new SearchEvent.QueryChanged("娜迦"));
        var s2 = SearchReducer.reduce(s1, new SearchEvent.SearchSubmitted());
        assertEquals(SearchState.Page.RESULTS, s2.currentPage());
        assertEquals(new SearchQuery("娜迦"), s2.query());

        // Same result if applied in direct sequence
        var direct = SearchReducer.reduce(
            SearchReducer.reduce(state, new SearchEvent.QueryChanged("娜迦")),
            new SearchEvent.SearchSubmitted()
        );
        assertEquals(s2, direct);
    }
}
