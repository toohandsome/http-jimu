package com.jimu.http.cache;

import java.lang.reflect.Type;

public interface JimuCacheProvider {

    <T> T get(String cacheName, String key, Type type);

    void put(String cacheName, String key, Object value, long ttlMs);

    void evict(String cacheName, String key);

    void clear(String cacheName);

    default <T> T get(String cacheName, String key, Class<T> type) {
        return get(cacheName, key, (Type) type);
    }
}

