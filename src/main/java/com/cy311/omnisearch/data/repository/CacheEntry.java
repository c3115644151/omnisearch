package com.cy311.omnisearch.data.repository;

/**
 * Wrapper for cached data with a timestamp for TTL checking.
 */
public record CacheEntry<T>(T data, long timestamp) {}
