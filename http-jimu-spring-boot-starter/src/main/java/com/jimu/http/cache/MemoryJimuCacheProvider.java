package com.jimu.http.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * FIX (Issue 6): Replaced unbounded ConcurrentHashMap with Caffeine-backed caches.
 * Each cache namespace is a separate Caffeine cache with bounded size and per-entry TTL eviction,
 * preventing unbounded heap growth (OOM) while preserving the original cache contract.
 */
public class MemoryJimuCacheProvider implements JimuCacheProvider {

    private static final int DEFAULT_MAX_SIZE = 1000;
    private static final long DEFAULT_TTL_NS = TimeUnit.DAYS.toNanos(3650); // practical "no-expire"

    // namespace -> Caffeine Cache
    private final ConcurrentMap<String, Cache<String, CacheEntry>> namespaces = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Type type) {
        if (cacheName == null || key == null) {
            return null;
        }
        Cache<String, CacheEntry> cache = namespaces.get(cacheName);
        if (cache == null) {
            return null;
        }
        CacheEntry entry = cache.getIfPresent(key);
        return entry == null ? null : (T) entry.value;
    }

    @Override
    public void put(String cacheName, String key, Object value, long ttlMs) {
        if (cacheName == null || key == null || value == null) {
            return;
        }
        Cache<String, CacheEntry> cache = namespaces.computeIfAbsent(cacheName, name ->
                Caffeine.newBuilder()
                        .maximumSize(DEFAULT_MAX_SIZE)
                        .expireAfter(new CacheEntryExpiry())
                        .build());
        cache.put(key, new CacheEntry(value, ttlMs));
    }

    @Override
    public void evict(String cacheName, String key) {
        if (cacheName == null || key == null) {
            return;
        }
        Cache<String, CacheEntry> cache = namespaces.get(cacheName);
        if (cache != null) {
            cache.invalidate(key);
        }
    }

    @Override
    public void clear(String cacheName) {
        if (cacheName == null) {
            return;
        }
        Cache<String, CacheEntry> cache = namespaces.remove(cacheName);
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    private static final class CacheEntry {
        private final Object value;
        private final long ttlNanos;

        private CacheEntry(Object value, long ttlMs) {
            this.value = value;
            this.ttlNanos = ttlMs > 0 ? TimeUnit.MILLISECONDS.toNanos(ttlMs) : DEFAULT_TTL_NS;
        }
    }

    private static final class CacheEntryExpiry implements Expiry<String, CacheEntry> {
        @Override
        public long expireAfterCreate(String key, CacheEntry value, long currentTime) {
            return value.ttlNanos;
        }

        @Override
        public long expireAfterUpdate(String key, CacheEntry value, long currentTime, long currentDuration) {
            return value.ttlNanos;
        }

        @Override
        public long expireAfterRead(String key, CacheEntry value, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
