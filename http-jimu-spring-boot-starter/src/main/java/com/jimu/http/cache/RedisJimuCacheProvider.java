package com.jimu.http.cache;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisJimuCacheProvider implements JimuCacheProvider {

    private static final String PREFIX = "http-jimu:cache:";
    private static final int SCAN_BATCH_SIZE = 100;

    private final StringRedisTemplate redisTemplate;

    public RedisJimuCacheProvider(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public <T> T get(String cacheName, String key, Type type) {
        if (cacheName == null || key == null) {
            return null;
        }
        String payload = redisTemplate.opsForValue().get(buildKey(cacheName, key));
        if (payload == null) {
            return null;
        }
        return JSON.parseObject(payload, type);
    }

    @Override
    public void put(String cacheName, String key, Object value, long ttlMs) {
        if (cacheName == null || key == null || value == null) {
            return;
        }
        String redisKey = buildKey(cacheName, key);
        String payload = JSON.toJSONString(value);
        if (ttlMs > 0) {
            redisTemplate.opsForValue().set(redisKey, payload, ttlMs, TimeUnit.MILLISECONDS);
        } else {
            redisTemplate.opsForValue().set(redisKey, payload);
        }
    }

    @Override
    public void evict(String cacheName, String key) {
        if (cacheName == null || key == null) {
            return;
        }
        redisTemplate.delete(buildKey(cacheName, key));
    }

    @Override
    public void clear(String cacheName) {
        if (cacheName == null) {
            return;
        }
        String pattern = PREFIX + cacheName + ":*";
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build();
        Set<String> keysToDelete = new HashSet<>();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keysToDelete.add(cursor.next());
                if (keysToDelete.size() >= SCAN_BATCH_SIZE) {
                    redisTemplate.delete(keysToDelete);
                    keysToDelete.clear();
                }
            }
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
            }
        } catch (Exception e) {
            log.warn("Failed to scan and delete cache keys for pattern: {}", pattern, e);
        }
    }

    private String buildKey(String cacheName, String key) {
        return PREFIX + cacheName + ":" + key;
    }
}
