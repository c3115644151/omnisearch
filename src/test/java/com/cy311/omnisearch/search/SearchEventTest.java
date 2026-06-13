package com.cy311.omnisearch.search;

import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.document.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchEventTest {

    @Test
    void queryChanged_creates() {
        var event = new SearchEvent.QueryChanged("娜迦");
        assertEquals("娜迦", event.query());
    }

    @Test
    void searchSubmitted_creates() {
        var event = new SearchEvent.SearchSubmitted();
        assertNotNull(event);
    }

    @Test
    void resultSelected_creates() {
        var event = new SearchEvent.ResultSelected(0);
        assertEquals(0, event.index());
    }

    @Test
    void detailLoaded_creates() {
        var doc = new Document("Title", null, null, List.of());
        var page = new ItemPage("id1", "Title", "Mod", doc, "url");
        var event = new SearchEvent.DetailLoaded(page);
        assertEquals(page, event.page());
    }

    @Test
    void linkClicked_creates() {
        var event = new SearchEvent.LinkClicked("https://www.mcmod.cn/item/123.html");
        assertEquals("https://www.mcmod.cn/item/123.html", event.url());
    }

    @Test
    void goBack_creates() {
        var event = new SearchEvent.GoBack();
        assertNotNull(event);
    }

    @Test
    void captchaSolved_creates() {
        var event = new SearchEvent.CaptchaSolved("solution123");
        assertEquals("solution123", event.solution());
    }

    @Test
    void errorOccurred_creates() {
        var event = new SearchEvent.ErrorOccurred("Something went wrong");
        assertEquals("Something went wrong", event.message());
    }

    @Test
    void dismiss_creates() {
        var event = new SearchEvent.Dismiss();
        assertNotNull(event);
    }

    @Test
    void allEventsImplementSealedInterface() {
        assertTrue(SearchEvent.class.isSealed());
    }

    @Test
    void queryChanged_implementsSearchEvent() {
        assertInstanceOf(SearchEvent.class, new SearchEvent.QueryChanged("test"));
    }

    @Test
    void searchSubmitted_implementsSearchEvent() {
        assertInstanceOf(SearchEvent.class, new SearchEvent.SearchSubmitted());
    }

    @Test
    void resultSelected_implementsSearchEvent() {
        assertInstanceOf(SearchEvent.class, new SearchEvent.ResultSelected(1));
    }

    @Test
    void detailLoaded_implementsSearchEvent() {
        var doc = new Document("T", null, null, List.of());
        assertInstanceOf(SearchEvent.class, new SearchEvent.DetailLoaded(
            new ItemPage("id", "title", "mod", doc, "url")
        ));
    }

    @Test
    void linkClicked_implementsSearchEvent() {
        assertInstanceOf(SearchEvent.class, new SearchEvent.LinkClicked("url"));
    }

    @Test
    void goBack_implementsSearchEvent() {
        assertInstanceOf(SearchEvent.class, new SearchEvent.GoBack());
    }

    @Test
    void captchaSolved_implementsSearchEvent() {
        assertInstanceOf(SearchEvent.class, new SearchEvent.CaptchaSolved("solution"));
    }

    @Test
    void errorOccurred_implementsSearchEvent() {
        assertInstanceOf(SearchEvent.class, new SearchEvent.ErrorOccurred("msg"));
    }

    @Test
    void dismiss_implementsSearchEvent() {
        assertInstanceOf(SearchEvent.class, new SearchEvent.Dismiss());
    }

    @Test
    void queryChanged_equality() {
        assertEquals(new SearchEvent.QueryChanged("a"), new SearchEvent.QueryChanged("a"));
        assertNotEquals(new SearchEvent.QueryChanged("a"), new SearchEvent.QueryChanged("b"));
    }

    @Test
    void resultSelected_equality() {
        assertEquals(new SearchEvent.ResultSelected(0), new SearchEvent.ResultSelected(0));
        assertNotEquals(new SearchEvent.ResultSelected(0), new SearchEvent.ResultSelected(1));
    }

    @Test
    void errorOccurred_equality() {
        assertEquals(
            new SearchEvent.ErrorOccurred("msg"),
            new SearchEvent.ErrorOccurred("msg")
        );
        assertNotEquals(
            new SearchEvent.ErrorOccurred("msg1"),
            new SearchEvent.ErrorOccurred("msg2")
        );
    }

    @Test
    void singletonEvents_areDifferentTypes() {
        var submitted = new SearchEvent.SearchSubmitted();
        var goBack = new SearchEvent.GoBack();
        var dismiss = new SearchEvent.Dismiss();
        assertNotEquals(submitted, goBack);
        assertNotEquals(submitted, dismiss);
        assertNotEquals(goBack, dismiss);
    }
}
