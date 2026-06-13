package com.cy311.omnisearch.data.repository;

import com.cy311.omnisearch.data.model.*;
import com.cy311.omnisearch.data.model.document.DocNodeAdapterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public class CacheLayer {
    // TTL configuration (milliseconds)
    private static final long SEARCH_TTL_MS = 7 * 24 * 60 * 60 * 1000L;   // 7 days
    private static final long PAGE_TTL_MS = 30 * 24 * 60 * 60 * 1000L;    // 30 days
    private static final long STALE_RETENTION_MS = 90 * 24 * 60 * 60 * 1000L; // 90 days

    private final Path cacheDir;
    private final Gson gson;

    public CacheLayer(Path cacheDir) {
        this(cacheDir, new GsonBuilder()
            .registerTypeAdapterFactory(new DocNodeAdapterFactory())
            .create());
    }

    /**
     * Creates a CacheLayer with a custom Gson instance (for testing or custom config).
     */
    CacheLayer(Path cacheDir, Gson gson) {
        this.cacheDir = cacheDir;
        this.gson = gson;
    }

    // === Search result cache ===

    public @Nullable List<SearchHit> getSearchResults(SearchQuery query) {
        Type dataType = new TypeToken<List<SearchHit>>() {}.getType();
        return getEntry(searchPath(query), SEARCH_TTL_MS, dataType);
    }

    public void putSearchResults(SearchQuery query, List<SearchHit> results) {
        Path fresh = searchPath(query);
        Path stale = staleSearchPath(query);
        moveToStale(fresh, stale);
        saveEntry(fresh, results);
    }

    public @Nullable List<SearchHit> getSearchResultsStale(SearchQuery query) {
        Type dataType = new TypeToken<List<SearchHit>>() {}.getType();
        return getEntry(staleSearchPath(query), STALE_RETENTION_MS, dataType);
    }

    // === Page cache ===

    public @Nullable ItemPage getPage(String pageId) {
        return getEntry(pagePath(pageId), PAGE_TTL_MS, ItemPage.class);
    }

    public void putPage(String pageId, ItemPage page) {
        Path fresh = pagePath(pageId);
        Path stale = stalePagePath(pageId);
        moveToStale(fresh, stale);
        saveEntry(fresh, page);
    }

    public @Nullable ItemPage getPageStale(String pageId) {
        return getEntry(stalePagePath(pageId), STALE_RETENTION_MS, ItemPage.class);
    }

    public void clear() {
        deleteDirectory(cacheDir);
    }

    // === Internal methods ===

    @SuppressWarnings("unchecked")
    private @Nullable <T> T getEntry(Path path, long ttlMs, Type dataType) {
        if (!Files.exists(path)) return null;
        try {
            String json = Files.readString(path);
            Type entryType = TypeToken.getParameterized(CacheEntry.class, dataType).getType();
            CacheEntry<T> entry = gson.fromJson(json, entryType);
            if (entry == null) return null;
            long age = System.currentTimeMillis() - entry.timestamp();
            if (age > ttlMs) return null; // expired
            return entry.data();
        } catch (Exception e) {
            return null; // corrupted file → treat as cache miss
        }
    }

    private <T> void saveEntry(Path path, T data) {
        try {
            Files.createDirectories(path.getParent());
            CacheEntry<T> entry = new CacheEntry<>(data, System.currentTimeMillis());
            String json = gson.toJson(entry);
            Files.writeString(path, json);
        } catch (IOException e) {
            // Cache write failure is non-fatal
        }
    }

    private void moveToStale(Path fresh, Path stale) {
        if (Files.exists(fresh)) {
            try {
                Files.createDirectories(stale.getParent());
                Files.move(fresh, stale, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Stale move failure is non-fatal; new cache will still be written
            }
        }
    }

    private Path searchPath(SearchQuery query) {
        return cacheDir.resolve("search").resolve(md5(query.text()) + ".json");
    }

    private Path staleSearchPath(SearchQuery query) {
        return cacheDir.resolve("stale").resolve("search").resolve(md5(query.text()) + ".json");
    }

    private Path pagePath(String pageId) {
        return cacheDir.resolve("page").resolve(pageId.replace("/", "_") + ".json");
    }

    private Path stalePagePath(String pageId) {
        return cacheDir.resolve("stale").resolve("page").resolve(pageId.replace("/", "_") + ".json");
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static void deleteDirectory(Path dir) {
        if (!Files.exists(dir)) return;
        try {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }
}
