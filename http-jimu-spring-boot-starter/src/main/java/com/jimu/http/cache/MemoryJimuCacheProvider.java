package com.jimu.http.cache;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryJimuCacheProvider implements JimuCacheProvider {

    private final Map<String, Map<String, CacheEntry>> namespaces = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Type type) {
        if (cacheName == null || key == null) {
            return null;
        }
        Map<String, CacheEntry> ns = namespaces.get(cacheName);
        if (ns == null) {
            return null;
        }
        CacheEntry entry = ns.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt > 0 && System.currentTimeMillis() >= entry.expireAt) {
            ns.remove(key);
            return null;
        }
        return (T) entry.value;
    }

    @Override
    public void put(String cacheName, String key, Object value, long ttlMs) {
        if (cacheName == null || key == null || value == null) {
            return;
        }
        Map<String, CacheEntry> ns = namespaces.computeIfAbsent(cacheName, name -> new ConcurrentHashMap<>());
        CacheEntry entry = new CacheEntry();
        entry.value = value;
        entry.expireAt = ttlMs > 0 ? System.currentTimeMillis() + ttlMs : 0L;
        ns.put(key, entry);
    }

    @Override
    public void evict(String cacheName, String key) {
        if (cacheName == null || key == null) {
            return;
        }
        Map<String, CacheEntry> ns = namespaces.get(cacheName);
        if (ns != null) {
            ns.remove(key);
        }
    }

    @Override
    public void clear(String cacheName) {
        if (cacheName == null) {
            return;
        }
        namespaces.remove(cacheName);
    }

    private static class CacheEntry {
        private Object value;
        private long expireAt;
    }
}

