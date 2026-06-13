package com.cy311.omnisearch.data.repository;

import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;
import com.cy311.omnisearch.data.model.document.DocNodeAdapterFactory;
import com.cy311.omnisearch.data.model.document.Document;
import com.cy311.omnisearch.data.model.document.TextNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CacheLayerTest {

    @TempDir
    Path tempDir;

    private CacheLayer cache;
    private Gson gson;

    @BeforeEach
    void setUp() {
        cache = new CacheLayer(tempDir);
        gson = new GsonBuilder()
            .registerTypeAdapterFactory(new DocNodeAdapterFactory())
            .create();
    }

    // ══════════════════════════════════════════════
    // Search result cache
    // ══════════════════════════════════════════════

    @Test
    void searchCache_putGet_returnsSameData() {
        var query = new SearchQuery("test search");
        var results = List.of(
            new SearchHit("id1", "result1", "mod", "source"),
            new SearchHit("id2", "result2", "item", "source")
        );

        cache.putSearchResults(query, results);
        var cached = cache.getSearchResults(query);

        assertNotNull(cached);
        assertEquals(results, cached);
    }

    @Test
    void searchCache_expiredEntry_returnsNull() {
        var query = new SearchQuery("test search");

        // Write a CacheEntry with an old timestamp directly
        Path cacheFile = tempDir.resolve("search").resolve(md5("test search") + ".json");
        var oldEntry = new CacheEntry<List<SearchHit>>(
            List.of(new SearchHit("id1", "old", "mod", "source")),
            0L // epoch timestamp → definitely expired
        );
        writeEntry(cacheFile, oldEntry);

        // Verify expired → null
        assertNull(cache.getSearchResults(query));
    }

    @Test
    void searchCache_freshEntry_returnsData() {
        var query = new SearchQuery("fresh query");
        var results = List.of(
            new SearchHit("id1", "fresh", "mod", "source")
        );

        cache.putSearchResults(query, results);

        // Read immediately → should be fresh
        var cached = cache.getSearchResults(query);
        assertNotNull(cached);
        assertEquals(results, cached);
    }

    // ══════════════════════════════════════════════
    // Page cache
    // ══════════════════════════════════════════════

    @Test
    void pageCache_putGet_returnsSameData() {
        var page = new ItemPage(
            "item/123",
            "Test Item",
            "TestMod",
            new Document("Doc", null, null, List.of(new TextNode("content"))),
            "https://example.com/item/123"
        );

        cache.putPage("item/123", page);
        var cached = cache.getPage("item/123");

        assertNotNull(cached);
        assertEquals(page, cached);
    }

    // ══════════════════════════════════════════════
    // Cache miss
    // ══════════════════════════════════════════════

    @Test
    void searchCache_miss_returnsNull() {
        var query = new SearchQuery("nonexistent");
        assertNull(cache.getSearchResults(query));
    }

    @Test
    void pageCache_miss_returnsNull() {
        assertNull(cache.getPage("nonexistent/999"));
    }

    // ══════════════════════════════════════════════
    // Empty query
    // ══════════════════════════════════════════════

    @Test
    void searchCache_emptyQuery_worksCorrectly() {
        var query = new SearchQuery("");
        var results = List.of(
            new SearchHit("id1", "empty query result", "mod", "source")
        );

        cache.putSearchResults(query, results);
        var cached = cache.getSearchResults(query);

        assertNotNull(cached);
        assertEquals(results, cached);
    }

    // ══════════════════════════════════════════════
    // Clear
    // ══════════════════════════════════════════════

    @Test
    void clear_removesAllCachedData() {
        var query = new SearchQuery("clear test");
        var results = List.of(
            new SearchHit("id1", "to be cleared", "mod", "source")
        );
        cache.putSearchResults(query, results);

        var page = new ItemPage(
            "item/999", "Clear Page", null,
            new Document("Doc", null, null, List.of(new TextNode("content"))),
            null
        );
        cache.putPage("item/999", page);

        cache.clear();

        assertNull(cache.getSearchResults(query));
        assertNull(cache.getPage("item/999"));
    }

    // ══════════════════════════════════════════════
    // Corrupted file
    // ══════════════════════════════════════════════

    @Test
    void searchCache_corruptedFile_returnsNull() throws IOException {
        var query = new SearchQuery("corrupted");

        // Write garbage to the cache file
        Path cacheFile = tempDir.resolve("search").resolve(md5("corrupted") + ".json");
        Files.createDirectories(cacheFile.getParent());
        Files.writeString(cacheFile, "this is not valid json");

        // Should not throw, should return null
        assertNull(cache.getSearchResults(query));
    }

    // ══════════════════════════════════════════════
    // Stale cache (move on put)
    // ══════════════════════════════════════════════

    @Test
    void putSearchResults_movesOldToStale() {
        var query = new SearchQuery("stale test");
        var oldResults = List.of(
            new SearchHit("id1", "old data", "mod", "source")
        );
        var newResults = List.of(
            new SearchHit("id2", "new data", "mod", "source")
        );

        // First put → creates fresh cache (no stale yet)
        cache.putSearchResults(query, oldResults);

        // Second put → should move old to stale
        cache.putSearchResults(query, newResults);

        // Fresh should return new data
        var fresh = cache.getSearchResults(query);
        assertNotNull(fresh);
        assertEquals(newResults, fresh);

        // We can't easily test stale via TTL here because fresh is still valid.
        // This test verifies the move doesn't break normal operation.
    }

    // ══════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════

    private void writeEntry(Path path, Object entry) {
        try {
            Files.createDirectories(path.getParent());
            String json = gson.toJson(entry);
            Files.writeString(path, json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
