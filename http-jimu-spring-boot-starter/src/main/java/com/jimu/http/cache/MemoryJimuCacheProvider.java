package com.jimu.http.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * FIX (Issue 6): Replaced unbounded ConcurrentHashMap with Caffeine-backed caches.
 * Each cache namespace is a separate Caffeine cache with bounded size and TTL-based eviction,
 * preventing unbounded heap growth (OOM).
 */
public class MemoryJimuCacheProvider implements JimuCacheProvider {

    private static final int DEFAULT_MAX_SIZE = 1000;
    private static final long DEFAULT_TTL_MS = 60 * 60 * 1000L; // 1 hour

    // namespace -> Caffeine Cache
    private final ConcurrentMap<String, Cache<String, Object>> namespaces = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Type type) {
        if (cacheName == null || key == null) {
            return null;
        }
        Cache<String, Object> cache = namespaces.get(cacheName);
        if (cache == null) {
            return null;
        }
        return (T) cache.getIfPresent(key);
    }

    @Override
    public void put(String cacheName, String key, Object value, long ttlMs) {
        if (cacheName == null || key == null || value == null) {
            return;
        }
        // Create a namespace-specific cache lazily with the provided TTL.
        // If the namespace already exists with a different TTL the existing cache is reused (stable behaviour).
        Cache<String, Object> cache = namespaces.computeIfAbsent(cacheName, name ->
                Caffeine.newBuilder()
                        .maximumSize(DEFAULT_MAX_SIZE)
                        .expireAfterWrite(ttlMs > 0 ? ttlMs : DEFAULT_TTL_MS, TimeUnit.MILLISECONDS)
                        .build());
        cache.put(key, value);
    }

    @Override
    public void evict(String cacheName, String key) {
        if (cacheName == null || key == null) {
            return;
        }
        Cache<String, Object> cache = namespaces.get(cacheName);
        if (cache != null) {
            cache.invalidate(key);
        }
    }

    @Override
    public void clear(String cacheName) {
        if (cacheName == null) {
            return;
        }
        Cache<String, Object> cache = namespaces.remove(cacheName);
        if (cache != null) {
            cache.invalidateAll();
        }
    }
}
