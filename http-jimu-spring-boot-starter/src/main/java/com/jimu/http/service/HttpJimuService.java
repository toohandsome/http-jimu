package com.jimu.http.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jimu.http.cache.JimuCacheProvider;
import com.jimu.http.cache.MemoryJimuCacheProvider;
import com.jimu.http.config.JimuProperties;
import com.jimu.http.engine.HttpJimuEngine;
import com.jimu.http.engine.HttpJimuScheduler;
import com.jimu.http.engine.model.ExecuteDetail;
import com.jimu.http.engine.model.PreviewDetail;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.mapper.HttpJimuConfigMapper;
import com.jimu.http.support.HttpJimuConfigSupport;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(JimuProperties.class)
public class HttpJimuService extends ServiceImpl<HttpJimuConfigMapper, HttpJimuConfig> {

    private static final String CACHE_NAME_HTTP_ID = "http_config_by_http_id";

    private final HttpJimuEngine engine;
    private final HttpJimuScheduler jimuScheduler;
    private final JimuProperties jimuProperties;
    private final JimuCacheProvider fallbackCacheProvider = new MemoryJimuCacheProvider();

    @Autowired(required = false)
    private JimuCacheProvider cacheProvider;

    @PostConstruct
    public void initScheduledTasks() {
        List<HttpJimuConfig> configs = this.list(new LambdaQueryWrapper<HttpJimuConfig>()
                .eq(HttpJimuConfig::getEnableJob, true));
        for (HttpJimuConfig config : configs) {
            String cronError = HttpJimuConfigSupport.validateCronConfig(config);
            if (cronError != null) {
                log.warn("Skip scheduling invalid config id={}, httpId={}, reason={}",
                        config.getId(), config.getHttpId(), cronError);
                continue;
            }
            jimuScheduler.schedule(config);
        }
    }

    @Override
    @Transactional
    public boolean saveOrUpdate(HttpJimuConfig entity) {
        String cronError = HttpJimuConfigSupport.validateCronConfig(entity);
        if (cronError != null) {
            throw new IllegalArgumentException(cronError);
        }
        String methodError = HttpJimuConfigSupport.validateAndNormalizeMethod(entity);
        if (methodError != null) {
            throw new IllegalArgumentException(methodError);
        }
        if (entity != null && entity.getCronConfig() != null) {
            entity.setCronConfig(entity.getCronConfig().trim());
        }
        if (entity != null && entity.getHttpId() != null) {
            entity.setHttpId(entity.getHttpId().trim());
        }

        String oldHttpId = null;
        if (entity != null && entity.getId() != null) {
            HttpJimuConfig old = this.getById(entity.getId());
            if (old != null) {
                oldHttpId = old.getHttpId();
            }
        }

        if (entity != null && entity.getHttpId() != null && !entity.getHttpId().isBlank()) {
            boolean httpIdChanged = oldHttpId == null || !oldHttpId.equals(entity.getHttpId());
            if (httpIdChanged) {
                long count = this.count(new LambdaQueryWrapper<HttpJimuConfig>()
                        .eq(HttpJimuConfig::getHttpId, entity.getHttpId()));
                if (count > 0) {
                    throw new IllegalArgumentException("httpId already exists: " + entity.getHttpId());
                }
            }
        }

        boolean success = super.saveOrUpdate(entity);
        if (success) {
            final HttpJimuConfig scheduledEntity = entity;
            final String previousHttpId = oldHttpId;
            String currentHttpId = entity != null ? entity.getHttpId() : null;
            runAfterCommitOrNow(() -> {
                jimuScheduler.schedule(scheduledEntity);
                evictHttpIdCache(previousHttpId);
                evictHttpIdCache(currentHttpId);
            });
        }
        return success;
    }

    @Override
    @Transactional
    public boolean removeById(Serializable id) {
        HttpJimuConfig old = this.getById(id);
        boolean success = super.removeById(id);
        if (success) {
            runAfterCommitOrNow(() -> {
                jimuScheduler.cancel(id.toString());
                if (old != null) {
                    evictHttpIdCache(old.getHttpId());
                }
            });
        }
        return success;
    }

    public String call(String httpId, Map<String, Object> params) {
        return callWithDetail(httpId, params).getResponseBody();
    }

    public ExecuteDetail callWithDetail(String httpId, Map<String, Object> params) {
        HttpJimuConfig config = getByHttpId(httpId);
        if (config == null) {
            throw new RuntimeException("HTTP Config not found: " + httpId);
        }
        return engine.executeWithDetail(config, params);
    }

    public PreviewDetail preview(String httpId, Map<String, Object> params) {
        HttpJimuConfig config = getByHttpId(httpId);
        if (config == null) {
            throw new RuntimeException("HTTP Config not found: " + httpId);
        }
        return engine.previewWithSteps(config, params);
    }

    public HttpJimuConfig getByHttpId(String httpId) {
        if (httpId == null || httpId.isBlank()) {
            return null;
        }
        HttpJimuConfig cached = cache().get(CACHE_NAME_HTTP_ID, httpId, HttpJimuConfig.class);
        if (cached != null) {
            return cached;
        }
        HttpJimuConfig config = this.getOne(new LambdaQueryWrapper<HttpJimuConfig>()
                .eq(HttpJimuConfig::getHttpId, httpId));
        if (config == null) {
            cache().evict(CACHE_NAME_HTTP_ID, httpId);
            return null;
        }
        cache().put(CACHE_NAME_HTTP_ID, httpId, config, jimuProperties.getCache().getHttpIdTtlMs());
        return config;
    }

    public void evictHttpIdCache(String httpId) {
        if (httpId == null || httpId.isBlank()) {
            return;
        }
        cache().evict(CACHE_NAME_HTTP_ID, httpId);
    }

    public void clearHttpIdCache() {
        cache().clear(CACHE_NAME_HTTP_ID);
    }

    private void runAfterCommitOrNow(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private JimuCacheProvider cache() {
        return cacheProvider != null ? cacheProvider : fallbackCacheProvider;
    }
}
