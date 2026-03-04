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

    @Scheduled(cron = "0 0 2 * * ?") // Every day at 2:00 AM
    public void cleanOldLogs() {
        log.info("Starting job log cleanup...");
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(7);
            boolean success = this.remove(new LambdaQueryWrapper<HttpJimuJobLog>()
                    .le(HttpJimuJobLog::getCreateTime, threshold));
            log.info("Job log cleanup finished. Success: {}", success);
        } catch (Exception e) {
            log.error("Job log cleanup failed", e);
        }
    }
}
