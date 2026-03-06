package com.jimu.http.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jimu.http.entity.HttpJimuJobLog;
import com.jimu.http.mapper.HttpJimuJobLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class HttpJimuJobLogService extends ServiceImpl<HttpJimuJobLogMapper, HttpJimuJobLog> {

    /**
     * FIX (Issue 9): Async saving to prevent blocking the scheduler thread.
     */
    public void saveAsync(HttpJimuJobLog logEntry) {
        if (logEntry == null) return;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
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
                                        .le(HttpJimuJobLog::getCreateTime, threshold));
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
}
