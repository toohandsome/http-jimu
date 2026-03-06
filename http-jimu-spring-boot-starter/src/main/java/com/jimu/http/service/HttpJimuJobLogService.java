package com.jimu.http.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jimu.http.entity.HttpJimuJobLog;
import com.jimu.http.mapper.HttpJimuJobLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class HttpJimuJobLogService extends ServiceImpl<HttpJimuJobLogMapper, HttpJimuJobLog> implements DisposableBean {

    private static final int LOG_SAVE_QUEUE_CAPACITY = 1000;
    private static final Duration LOG_SAVE_KEEP_ALIVE = Duration.ofSeconds(30);

    private Executor logSaveExecutor = createDefaultExecutor();

    /**
     * FIX (Issue 9): Async saving to prevent blocking the scheduler thread.
     */
    public void saveAsync(HttpJimuJobLog logEntry) {
        if (logEntry == null) {
            return;
        }
        logSaveExecutor.execute(() -> {
            try {
                this.save(logEntry);
            } catch (Exception e) {
                log.error("Failed to async save job log", e);
            }
        });
    }

    /**
     * FIX (Issue 10): Batch delete to prevent long-range slow queries on large tables.
     */
    @Scheduled(cron = "0 0 2 * * ?") // Every day at 2:00 AM
    public void cleanOldLogs() {
        log.info("Starting job log cleanup...");
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(7);
            int batchSize = 1000;
            long totalDeleted = 0;
            while (true) {
                com.baomidou.mybatisplus.extension.plugins.pagination.Page<HttpJimuJobLog> page =
                        this.page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, batchSize, false),
                                new LambdaQueryWrapper<HttpJimuJobLog>()
                                        .select(HttpJimuJobLog::getId)
                                        .le(HttpJimuJobLog::getCreateTime, threshold)
                                        .orderByAsc(HttpJimuJobLog::getId));
                java.util.List<HttpJimuJobLog> logs = page.getRecords();
                if (logs == null || logs.isEmpty()) {
                    break;
                }
                java.util.List<Long> ids = logs.stream().map(HttpJimuJobLog::getId).collect(java.util.stream.Collectors.toList());
                this.removeByIds(ids);
                totalDeleted += ids.size();
                if (ids.size() < batchSize) {
                    break;
                }
            }
            log.info("Job log cleanup finished. Total deleted: {}", totalDeleted);
        } catch (Exception e) {
            log.error("Job log cleanup failed", e);
        }
    }

    void setLogSaveExecutor(Executor logSaveExecutor) {
        this.logSaveExecutor = Objects.requireNonNull(logSaveExecutor, "logSaveExecutor");
    }

    @Override
    public void destroy() throws InterruptedException {
        if (logSaveExecutor instanceof ThreadPoolExecutor executor) {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    private Executor createDefaultExecutor() {
        int threads = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors()));
        AtomicInteger counter = new AtomicInteger();
        return new ThreadPoolExecutor(
                threads,
                threads,
                LOG_SAVE_KEEP_ALIVE.toSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(LOG_SAVE_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "http-jimu-job-log-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
