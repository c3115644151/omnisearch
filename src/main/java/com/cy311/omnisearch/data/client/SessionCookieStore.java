package com.cy311.omnisearch.data.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe session cookie storage with snapshot-based reads.
 * <p>
 * Requests read an immutable snapshot of cookies to avoid holding
 * references to the mutable internal state. Response cookies are
 * merged back into the store after the request completes.
 */
public final class SessionCookieStore {
    private final ConcurrentMap<String, String> cookies = new ConcurrentHashMap<>();

    /**
     * Returns a mutable snapshot of the current cookies.
     * Safe to pass to Jsoup or other HTTP clients; modifications
     * to the returned map do not affect the internal store.
     */
    public Map<String, String> snapshot() {
        return new ConcurrentHashMap<>(cookies);
    }

    /**
     * Merges response cookies into the store.
     * New cookies overwrite existing ones with the same key.
     */
    public void merge(Map<String, String> responseCookies) {
        if (responseCookies != null && !responseCookies.isEmpty()) {
            cookies.putAll(responseCookies);
        }
    }

    /**
     * Clears all cookies. Mainly for testing or session reset.
     */
    public void clear() {
        cookies.clear();
    }

    /**
     * Returns the number of cookies currently stored.
     */
    public int size() {
        return cookies.size();
    }

    /**
     * Returns true if the store contains the given key.
     */
    public boolean containsKey(String key) {
        return cookies.containsKey(key);
    }

    /**
     * Returns the value for the given key, or null if not present.
     */
    public String get(String key) {
        return cookies.get(key);
    }
}
