package com.jimu.http.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for HTTP Jimu.
 * These values can be overridden in application.yml with prefix "jimu".
 */
@Data
@ConfigurationProperties(prefix = "jimu")
public class JimuProperties {

    /**
     * Cache configuration.
     */
    private Cache cache = new Cache();

    /**
     * Scheduler configuration.
     */
    private Scheduler scheduler = new Scheduler();

    /**
     * Script configuration.
     */
    private Script script = new Script();

    @Data
    public static class Cache {
        /**
         * TTL for httpId -> HttpJimuConfig cache in milliseconds (default: 1 hour).
         */
        private long httpIdTtlMs = 60 * 60 * 1000L;

        /**
         * TTL for stepsConfig cache in milliseconds (default: 30 minutes).
         */
        private long stepsTtlMs = 30 * 60 * 1000L;

        /**
         * TTL for script metadata cache in milliseconds (default: 1 hour).
         */
        private long scriptMetaTtlMs = 60 * 60 * 1000L;

        /**
         * TTL for bean metadata cache in milliseconds (default: 10 minutes).
         */
        private long beanMetaTtlMs = 10 * 60 * 1000L;
    }

    @Data
    public static class Scheduler {
        /**
         * Default lock TTL in seconds (default: 120 seconds).
         */
        private long defaultLockTtlSeconds = 120L;

        /**
         * Minimum lock TTL in seconds (default: 30 seconds).
         */
        private long minLockTtlSeconds = 30L;

        /**
         * Thread pool size for scheduler (default: 5).
         */
        private int poolSize = 5;
    }

    @Data
    public static class Script {
        /**
         * Maximum number of cached script classes (default: 512).
         */
        private int cacheMax = 512;
    }
}
