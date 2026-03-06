package com.jimu.http.engine;

import com.jimu.http.config.JimuProperties;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.entity.HttpJimuJobLog;
import com.jimu.http.service.HttpJimuJobLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(JimuProperties.class)
public class HttpJimuScheduler {

    private static final String LOCK_VALUE_PREFIX = "LOCKED:";
    private static final long LOCK_DERIVE_EXTRA_SECONDS = 30L;

    private static final DefaultRedisScript<Long> SAFE_UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else " +
                    "return 0 " +
                    "end",
            Long.class
    );

    private final TaskScheduler taskScheduler;
    private final HttpJimuEngine engine;
    private final HttpJimuJobLogService jobLogService;
    private final JimuProperties jimuProperties;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private LockProvider jdbcLockProvider;

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    public void schedule(HttpJimuConfig config) {
        if (config == null || config.getId() == null || config.getId().isBlank()) {
            return;
        }
        cancel(config.getId());

        String cron = config.getCronConfig() != null ? config.getCronConfig().trim() : "";
        if (Boolean.TRUE.equals(config.getEnableJob()) && !cron.isBlank()) {
            try {
                ScheduledFuture<?> future = taskScheduler.schedule(() -> executeScheduled(config), new CronTrigger(cron));
                scheduledTasks.put(config.getId(), future);
                log.info("Successfully scheduled task: {} with cron: {}", config.getHttpId(), cron);
            } catch (Exception e) {
                log.error("Failed to schedule task: {}", config.getHttpId(), e);
            }
        }
    }

    public void cancel(String id) {
        ScheduledFuture<?> future = scheduledTasks.remove(id);
        if (future != null) {
            future.cancel(true);
            log.info("Cancelled scheduled task for ID: {}", id);
        }
        runningFlags.remove(id);
    }

    private void executeScheduled(HttpJimuConfig config) {
        log.info("Scheduled task triggering for HTTP Jimu: {} ({})", config.getName(), config.getHttpId());

        DistributedLock lock = acquireDistributedLock(config);
        if (!lock.acquired) {
            return;
        }

        AtomicBoolean running = runningFlags.computeIfAbsent(config.getId(), key -> new AtomicBoolean(false));
        if (!running.compareAndSet(false, true)) {
            log.warn("Skip overlapping task execution for httpId={} configId={}", config.getHttpId(), config.getId());
            lock.release();
            return;
        }

        long start = System.currentTimeMillis();
        Map<String, Object> params = new HashMap<>();
        String result = null;
        String status = "SUCCESS";
        String errorMsg = null;

        try {
            result = engine.execute(config, params);
        } catch (Exception e) {
            log.error("Error executing scheduled task: {}", config.getHttpId(), e);
            status = "ERROR";
            errorMsg = e.getMessage();
        } finally {
            long duration = System.currentTimeMillis() - start;
            try {
                // FIX (Issue 9): Use async saving to prevent blocking the scheduler thread and hurting throughput
                jobLogService.saveAsync(HttpJimuJobLog.builder()
                        .configId(config.getId())
                        .httpId(config.getHttpId())
                        .inputParams("{}")
                        .outputResult(result)
                        .status(status)
                        .errorMsg(errorMsg)
                        .duration(duration)
                        .createTime(LocalDateTime.now())
                        .build());
            } catch (Exception logEx) {
                log.error("Failed to save job log", logEx);
            } finally {
                running.set(false);
                lock.release();
            }
        }
    }

    private long resolveLockTtlSeconds(HttpJimuConfig config) {
        long ttlByTimeout = 0L;
        if (config != null) {
            if (config.getConnectTimeout() != null) {
                ttlByTimeout += config.getConnectTimeout();
            }
            if (config.getReadTimeout() != null) {
                ttlByTimeout += config.getReadTimeout();
            }
            if (config.getWriteTimeout() != null) {
                ttlByTimeout += config.getWriteTimeout();
            }
        }
        if (ttlByTimeout > 0) {
            long derived = TimeUnit.MILLISECONDS.toSeconds(ttlByTimeout) + LOCK_DERIVE_EXTRA_SECONDS;
            return Math.max(derived, jimuProperties.getScheduler().getMinLockTtlSeconds());
        }
        return jimuProperties.getScheduler().getDefaultLockTtlSeconds();
    }

    private void safeUnlock(String lockKey, String lockValue) {
        if (redisTemplate == null || lockKey == null || lockValue == null) {
            return;
        }
        try {
            redisTemplate.execute(SAFE_UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
        } catch (Exception unlockEx) {
            log.warn("Failed to release lock key={}", lockKey, unlockEx);
        }
    }

    private DistributedLock acquireDistributedLock(HttpJimuConfig config) {
        long ttlSeconds = resolveLockTtlSeconds(config);
        String jobKey = config != null ? config.getId() : null;

        if (redisTemplate != null) {
            String lockKey = "jimu:lock:job:" + jobKey;
            String lockValue = LOCK_VALUE_PREFIX + UUID.randomUUID();
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, ttlSeconds, TimeUnit.SECONDS);
                if (Boolean.TRUE.equals(acquired)) {
                    return DistributedLock.acquired(() -> safeUnlock(lockKey, lockValue));
                }
                log.debug("Task {} is locked by another instance via Redis, skipping.", config.getHttpId());
                return DistributedLock.notAcquired();
            } catch (Exception redisEx) {
                log.warn("Redis lock failed for task {}, degrade to next lock strategy.", config.getHttpId(), redisEx);
            }
        }

        if (jdbcLockProvider != null) {
            try {
                LockConfiguration lockConfiguration = new LockConfiguration(
                        Instant.now(),
                        "jimu:lock:job:" + jobKey,
                        Duration.ofSeconds(ttlSeconds),
                        Duration.ZERO
                );
                Optional<SimpleLock> simpleLock = jdbcLockProvider.lock(lockConfiguration);
                if (simpleLock.isPresent()) {
                    return DistributedLock.acquired(() -> {
                        try {
                            simpleLock.get().unlock();
                        } catch (Exception unlockEx) {
                            log.warn("Failed to release JDBC lock for job {}", jobKey, unlockEx);
                        }
                    });
                }
                log.debug("Task {} is locked by another instance via ShedLock, skipping.", config.getHttpId());
                return DistributedLock.notAcquired();
            } catch (Exception jdbcEx) {
                log.warn("ShedLock JDBC failed for task {}, degrade to single-node mode.", config.getHttpId(), jdbcEx);
            }
        }

        return DistributedLock.acquired(() -> {
            // single-node mode
        });
    }

    private static class DistributedLock {
        private final boolean acquired;
        private final Runnable releaser;

        private DistributedLock(boolean acquired, Runnable releaser) {
            this.acquired = acquired;
            this.releaser = releaser;
        }

        static DistributedLock acquired(Runnable releaser) {
            return new DistributedLock(true, releaser);
        }

        static DistributedLock notAcquired() {
            return new DistributedLock(false, () -> {
            });
        }

        void release() {
            releaser.run();
        }
    }

    @Configuration
    public static class SchedulerConfig {
        @Bean
        public TaskScheduler taskScheduler(JimuProperties jimuProperties) {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(jimuProperties.getScheduler().getPoolSize());
            scheduler.setThreadNamePrefix("jimu-scheduler-");
            scheduler.initialize();
            return scheduler;
        }
    }
}
