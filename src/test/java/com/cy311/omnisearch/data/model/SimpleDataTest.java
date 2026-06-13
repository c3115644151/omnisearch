package com.cy311.omnisearch.data.model;

import com.cy311.omnisearch.data.model.document.Document;
import com.cy311.omnisearch.data.model.document.TextNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleDataTest {

    @Test
    void searchQuery_creates() {
        var q = new SearchQuery("娜迦");
        assertEquals("娜迦", q.text());
    }

    @Test
    void searchQuery_equality() {
        assertEquals(new SearchQuery("test"), new SearchQuery("test"));
    }

    @Test
    void searchQuery_notEqual() {
        assertNotEquals(new SearchQuery("a"), new SearchQuery("b"));
    }

    @Test
    void searchHit_creates() {
        var hit = new SearchHit("item/123", "娜迦鳞片", "item", "暮色森林");
        assertEquals("item/123", hit.id());
        assertEquals("娜迦鳞片", hit.name());
        assertEquals("item", hit.type());
        assertEquals("暮色森林", hit.sourceMod());
    }

    @Test
    void searchHit_equality() {
        var a = new SearchHit("id1", "name1", "type1", "mod1");
        var b = new SearchHit("id1", "name1", "type1", "mod1");
        assertEquals(a, b);
    }

    @Test
    void searchHit_notEqual() {
        var a = new SearchHit("id1", "name1", "type1", "mod1");
        var b = new SearchHit("id2", "name1", "type1", "mod1");
        assertNotEquals(a, b);
    }

    @Test
    void itemPage_creates() {
        var doc = new Document("Title", null, null, List.of(new TextNode("content")));
        var page = new ItemPage("item/123", "娜迦鳞片", "暮色森林", doc, "https://www.mcmod.cn/item/123.html");
        assertEquals("item/123", page.id());
        assertEquals("娜迦鳞片", page.title());
        assertEquals("暮色森林", page.sourceMod());
        assertEquals(doc, page.document());
        assertEquals("https://www.mcmod.cn/item/123.html", page.url());
    }

    @Test
    void itemPage_equality() {
        var doc = new Document("T", null, null, List.of());
        var a = new ItemPage("id1", "title1", "mod1", doc, "url1");
        var b = new ItemPage("id1", "title1", "mod1", doc, "url1");
        assertEquals(a, b);
    }

    @Test
    void captchaContext_creates() {
        var ctx = new CaptchaContext("https://example.com/captcha.png", "captcha-123");
        assertEquals("https://example.com/captcha.png", ctx.captchaImageUrl());
        assertEquals("captcha-123", ctx.captchaId());
    }

    @Test
    void captchaContext_equality() {
        var a = new CaptchaContext("url1", "id1");
        var b = new CaptchaContext("url1", "id1");
        assertEquals(a, b);
    }

    @Test
    void allRecords_areInstanceOfRecord() {
        assertTrue(SearchQuery.class.isRecord());
        assertTrue(SearchHit.class.isRecord());
        assertTrue(ItemPage.class.isRecord());
        assertTrue(CaptchaContext.class.isRecord());
    }
}
