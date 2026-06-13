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

class SearchStateTest {

    @Test
    void initial_hasDefaultValues() {
        var state = SearchState.initial();
        assertEquals(SearchState.Page.SEARCH, state.currentPage());
        assertEquals(new SearchQuery(""), state.query());
        assertTrue(state.results().isEmpty());
        assertNull(state.detailPage());
        assertNotNull(state.navStack());
        assertEquals(SearchState.LoadingState.IDLE, state.loading());
        assertNull(state.errorMessage());
        assertNull(state.captcha());
    }

    @Test
    void initial_createsFreshNavStack() {
        var state1 = SearchState.initial();
        var state2 = SearchState.initial();
        assertNotNull(state1.navStack());
        assertNotNull(state2.navStack());
        // Each initial state has its own navigation stack
        assertNotSame(state1.navStack(), state2.navStack());
    }

    @Test
    void withPage_returnsNewInstance() {
        var state = SearchState.initial();
        var modified = state.withPage(SearchState.Page.RESULTS);
        assertNotSame(state, modified);
        assertEquals(SearchState.Page.SEARCH, state.currentPage());
        assertEquals(SearchState.Page.RESULTS, modified.currentPage());
    }

    @Test
    void withQuery_returnsNewInstance() {
        var state = SearchState.initial();
        var query = new SearchQuery("娜迦");
        var modified = state.withQuery(query);
        assertNotSame(state, modified);
        assertEquals(new SearchQuery(""), state.query());
        assertEquals(query, modified.query());
    }

    @Test
    void withResults_returnsNewInstance() {
        var state = SearchState.initial();
        var results = List.of(new SearchHit("item/123", "娜迦鳞片", "item", "暮色森林"));
        var modified = state.withResults(results);
        assertNotSame(state, modified);
        assertTrue(state.results().isEmpty());
        assertEquals(results, modified.results());
    }

    @Test
    void withDetailPage_returnsNewInstance() {
        var state = SearchState.initial();
        var doc = new Document("Title", null, null, List.of(new TextNode("content")));
        var page = new ItemPage("item/123", "Title", "Mod", doc, "url");
        var modified = state.withDetailPage(page);
        assertNotSame(state, modified);
        assertNull(state.detailPage());
        assertEquals(page, modified.detailPage());
    }

    @Test
    void withDetailPage_canSetNull() {
        var state = SearchState.initial();
        var modified = state.withDetailPage(null);
        assertNull(modified.detailPage());
    }

    @Test
    void withNavStack_returnsNewInstance() {
        var state = SearchState.initial();
        var newNav = new NavigationStack();
        var modified = state.withNavStack(newNav);
        assertNotSame(state, modified);
        assertSame(newNav, modified.navStack());
    }

    @Test
    void withLoading_returnsNewInstance() {
        var state = SearchState.initial();
        var modified = state.withLoading(SearchState.LoadingState.LOADING);
        assertNotSame(state, modified);
        assertEquals(SearchState.LoadingState.IDLE, state.loading());
        assertEquals(SearchState.LoadingState.LOADING, modified.loading());
    }

    @Test
    void withErrorMessage_returnsNewInstance() {
        var state = SearchState.initial();
        var modified = state.withErrorMessage("error occurred");
        assertNotSame(state, modified);
        assertNull(state.errorMessage());
        assertEquals("error occurred", modified.errorMessage());
    }

    @Test
    void withErrorMessage_canSetNull() {
        var state = SearchState.initial().withErrorMessage("error");
        var modified = state.withErrorMessage(null);
        assertNull(modified.errorMessage());
    }

    @Test
    void withCaptcha_returnsNewInstance() {
        var state = SearchState.initial();
        var captcha = new CaptchaContext("url", "id");
        var modified = state.withCaptcha(captcha);
        assertNotSame(state, modified);
        assertNull(state.captcha());
        assertEquals(captcha, modified.captcha());
    }

    @Test
    void withCaptcha_canSetNull() {
        var state = new CaptchaContext("url", "id");
        var original = SearchState.initial().withCaptcha(state);
        var modified = original.withCaptcha(null);
        assertNull(modified.captcha());
    }

    @Test
    void multipleWithCalls_chainCorrectly() {
        var results = List.of(new SearchHit("id1", "name1", "type1", "mod1"));
        var doc = new Document("Title", null, null, List.of());
        var detail = new ItemPage("id1", "Title", "Mod", doc, "url");

        var state = SearchState.initial()
            .withPage(SearchState.Page.DETAIL)
            .withQuery(new SearchQuery("test"))
            .withResults(results)
            .withDetailPage(detail)
            .withLoading(SearchState.LoadingState.LOADING);

        assertEquals(SearchState.Page.DETAIL, state.currentPage());
        assertEquals(new SearchQuery("test"), state.query());
        assertEquals(results, state.results());
        assertEquals(detail, state.detailPage());
        assertEquals(SearchState.LoadingState.LOADING, state.loading());
    }

    @Test
    void initial_loadingStateIsIdle() {
        assertEquals(SearchState.LoadingState.IDLE, SearchState.initial().loading());
    }

    @Test
    void initial_errorMessageIsNull() {
        assertNull(SearchState.initial().errorMessage());
    }

    @Test
    void initial_captchaIsNull() {
        assertNull(SearchState.initial().captcha());
    }

    @Test
    void initial_pageIsSearch() {
        assertEquals(SearchState.Page.SEARCH, SearchState.initial().currentPage());
    }

    @Test
    void initial_queryIsEmpty() {
        assertEquals(new SearchQuery(""), SearchState.initial().query());
    }

    @Test
    void initial_resultsIsEmpty() {
        assertTrue(SearchState.initial().results().isEmpty());
    }

    @Test
    void initial_detailPageIsNull() {
        assertNull(SearchState.initial().detailPage());
    }

    @Test
    void originalStateUnchanged_afterWithMethods() {
        var original = SearchState.initial();
        original.withPage(SearchState.Page.RESULTS);
        original.withQuery(new SearchQuery("test"));
        original.withLoading(SearchState.LoadingState.LOADING);
        // Original must remain unchanged
        assertEquals(SearchState.Page.SEARCH, original.currentPage());
        assertEquals(new SearchQuery(""), original.query());
        assertEquals(SearchState.LoadingState.IDLE, original.loading());
    }

    @Test
    void record_isRecord() {
        assertTrue(SearchState.class.isRecord());
    }
}
