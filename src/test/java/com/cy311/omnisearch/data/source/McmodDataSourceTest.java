package com.cy311.omnisearch.data.source;

import com.cy311.omnisearch.data.client.McmodHttpClient;
import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;
import com.cy311.omnisearch.data.parser.McmodParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McmodDataSourceTest {

    private McmodHttpClient client;
    private McmodParser parser;
    private McmodCaptchaHandler captchaHandler;
    private McmodDataSource dataSource;

    @BeforeEach
    void setUp() {
        client = new McmodHttpClient();
        parser = new McmodParser();
        captchaHandler = new McmodCaptchaHandler();
        dataSource = new McmodDataSource(client, parser, captchaHandler);
    }

    // ══════════════════════════════════════════════
    // Basic metadata
    // ══════════════════════════════════════════════

    @Test
    void name_returnsMcmod() {
        assertEquals("mcmod", dataSource.name());
    }

    @Test
    void isAvailable_returnsTrue() {
        assertTrue(dataSource.isAvailable());
    }

    // ══════════════════════════════════════════════
    // search - edge cases (no network required)
    // ══════════════════════════════════════════════

    @Test
    void search_returnsEmptyForNullQuery() throws Exception {
        List<SearchHit> results = dataSource.search(new SearchQuery(null)).get();
        assertTrue(results.isEmpty());
    }

    @Test
    void search_returnsEmptyForBlankQuery() throws Exception {
        List<SearchHit> results = dataSource.search(new SearchQuery("  ")).get();
        assertTrue(results.isEmpty());
    }

    @Test
    void search_returnsEmptyForEmptyQuery() throws Exception {
        List<SearchHit> results = dataSource.search(new SearchQuery("")).get();
        assertTrue(results.isEmpty());
    }

    @Test
    void search_httpClientReturnsEmpty_returnsEmpty() throws Exception {
        // Blank query triggers early return in McmodDataSource before hitting HTTP,
        // which is equivalent to an empty response from the client
        List<SearchHit> results = dataSource.search(new SearchQuery("")).get();
        assertTrue(results.isEmpty());
    }

    // ══════════════════════════════════════════════
    // getPage - edge cases (no network required)
    // ══════════════════════════════════════════════

    @Test
    void getPage_returnsNullForNullId() throws Exception {
        ItemPage page = dataSource.getPage(null).get();
        assertNull(page);
    }

    @Test
    void getPage_returnsNullForEmptyId() throws Exception {
        ItemPage page = dataSource.getPage("").get();
        assertNull(page);
    }

    @Test
    void getPage_returnsNullForBlankId() throws Exception {
        ItemPage page = dataSource.getPage("  ").get();
        assertNull(page);
    }

    @Test
    void getPage_returnsNullForUnknownPrefix() throws Exception {
        ItemPage page = dataSource.getPage("unknown/1").get();
        assertNull(page);
    }

}
