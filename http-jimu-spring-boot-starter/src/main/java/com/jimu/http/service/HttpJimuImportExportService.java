package com.jimu.http.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jimu.http.dto.HttpJimuExportBundle;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.entity.HttpJimuGroup;
import com.jimu.http.entity.HttpJimuPool;
import com.jimu.http.entity.HttpJimuStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HttpJimuImportExportService {

    private final HttpJimuGroupService groupService;
    private final HttpJimuPoolService poolService;
    private final HttpJimuStepService stepService;
    private final HttpJimuService httpJimuService;

    public HttpJimuExportBundle exportAll() {
        HttpJimuExportBundle bundle = new HttpJimuExportBundle();
        bundle.setExportedAt(System.currentTimeMillis());
        bundle.setGroups(groupService.list());
        bundle.setPools(poolService.list());
        bundle.setSteps(stepService.list());
        bundle.setConfigs(httpJimuService.list());
        return bundle;
    }

    @Transactional
    public void importAll(HttpJimuExportBundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("Import payload cannot be null");
        }
        Map<String, String> groupIdMap = importGroups(bundle.getGroups());
        Map<String, String> poolIdMap = importPools(bundle.getPools());
        importSteps(bundle.getSteps());
        importConfigs(bundle.getConfigs(), groupIdMap, poolIdMap);
    }

    private Map<String, String> importGroups(List<HttpJimuGroup> groups) {
        Map<String, String> idMap = new HashMap<>();
        if (groups == null) {
            return idMap;
        }
        for (HttpJimuGroup incoming : groups) {
            if (incoming == null || incoming.getCode() == null || incoming.getCode().isBlank()) {
                continue;
            }
            HttpJimuGroup existing = groupService.getOne(new LambdaQueryWrapper<HttpJimuGroup>()
                    .eq(HttpJimuGroup::getCode, incoming.getCode()));
            HttpJimuGroup target = existing != null ? existing : new HttpJimuGroup();
            String oldId = incoming.getId();
            target.setCode(incoming.getCode());
            target.setName(incoming.getName());
            target.setDescription(incoming.getDescription());
            target.setStepsConfig(normalizeStepsConfig(incoming.getStepsConfig()));
            if (existing == null) {
                target.setCreateTime(LocalDateTime.now());
            }
            target.setUpdateTime(LocalDateTime.now());
            groupService.saveOrUpdate(target);
            if (oldId != null) {
                idMap.put(oldId, target.getId());
            }
        }
        return idMap;
    }

    private Map<String, String> importPools(List<HttpJimuPool> pools) {
        Map<String, String> idMap = new HashMap<>();
        if (pools == null) {
            return idMap;
        }
        for (HttpJimuPool incoming : pools) {
            if (incoming == null || incoming.getName() == null || incoming.getName().isBlank()) {
                continue;
            }
            HttpJimuPool existing = poolService.getOne(new LambdaQueryWrapper<HttpJimuPool>()
                    .eq(HttpJimuPool::getName, incoming.getName()));
            HttpJimuPool target = existing != null ? existing : new HttpJimuPool();
            String oldId = incoming.getId();
            target.setName(incoming.getName());
            target.setMaxIdleConnections(incoming.getMaxIdleConnections());
            target.setKeepAliveDuration(incoming.getKeepAliveDuration());
            target.setConnectTimeout(incoming.getConnectTimeout());
            target.setReadTimeout(incoming.getReadTimeout());
            target.setWriteTimeout(incoming.getWriteTimeout());
            target.setCallTimeout(incoming.getCallTimeout());
            target.setRetryOnConnectionFailure(incoming.getRetryOnConnectionFailure());
            target.setFollowRedirects(incoming.getFollowRedirects());
            target.setFollowSslRedirects(incoming.getFollowSslRedirects());
            target.setRetryMaxAttempts(incoming.getRetryMaxAttempts());
            target.setRetryOnHttpStatus(incoming.getRetryOnHttpStatus());
            target.setMaxRequests(incoming.getMaxRequests());
            target.setMaxRequestsPerHost(incoming.getMaxRequestsPerHost());
            target.setPingInterval(incoming.getPingInterval());
            target.setProxyHost(incoming.getProxyHost());
            target.setProxyPort(incoming.getProxyPort());
            target.setProxyType(incoming.getProxyType());
            target.setStepsConfig(normalizeStepsConfig(incoming.getStepsConfig()));
            if (existing == null) {
                target.setCreateTime(LocalDateTime.now());
            }
            target.setUpdateTime(LocalDateTime.now());
            poolService.saveOrUpdate(target);
            if (oldId != null) {
                idMap.put(oldId, target.getId());
            }
        }
        return idMap;
    }

    private void importSteps(List<HttpJimuStep> steps) {
        if (steps == null) {
            return;
        }
        for (HttpJimuStep incoming : steps) {
            if (incoming == null || incoming.getCode() == null || incoming.getCode().isBlank()) {
                continue;
            }
            HttpJimuStep existing = stepService.getOne(new LambdaQueryWrapper<HttpJimuStep>()
                    .eq(HttpJimuStep::getCode, incoming.getCode()));
            HttpJimuStep target = existing != null ? existing : new HttpJimuStep();
            target.setCode(incoming.getCode());
            target.setName(incoming.getName());
            target.setType(incoming.getType());
            target.setTarget(incoming.getTarget());
            target.setScriptContent(incoming.getScriptContent());
            target.setConfigJson(incoming.getConfigJson());
            target.setInputSchema(incoming.getInputSchema());
            target.setOutputSchema(incoming.getOutputSchema());
            target.setDescription(incoming.getDescription());
            if (existing == null) {
                target.setCreateTime(LocalDateTime.now());
            }
            target.setUpdateTime(LocalDateTime.now());
            stepService.saveOrUpdate(target);
        }
    }

    private void importConfigs(List<HttpJimuConfig> configs, Map<String, String> groupIdMap, Map<String, String> poolIdMap) {
        if (configs == null) {
            return;
        }
        for (HttpJimuConfig incoming : configs) {
            if (incoming == null || incoming.getHttpId() == null || incoming.getHttpId().isBlank()) {
                continue;
            }
            HttpJimuConfig existing = httpJimuService.getOne(new LambdaQueryWrapper<HttpJimuConfig>()
                    .eq(HttpJimuConfig::getHttpId, incoming.getHttpId()));
            HttpJimuConfig target = existing != null ? existing : new HttpJimuConfig();
            target.setHttpId(incoming.getHttpId());
            target.setGroupId(groupIdMap.getOrDefault(incoming.getGroupId(), incoming.getGroupId()));
            target.setName(incoming.getName());
            target.setUrl(incoming.getUrl());
            target.setMethod(incoming.getMethod());
            target.setHeaders(incoming.getHeaders());
            target.setQueryParams(incoming.getQueryParams());
            target.setBodyConfig(incoming.getBodyConfig());
            target.setBodyType(incoming.getBodyType());
            target.setBodyRawType(incoming.getBodyRawType());
            target.setCronConfig(incoming.getCronConfig());
            target.setEnableJob(incoming.getEnableJob());
            target.setParamsConfig(incoming.getParamsConfig());
            target.setStepsConfig(normalizeStepsConfig(incoming.getStepsConfig()));
            target.setPoolId(poolIdMap.getOrDefault(incoming.getPoolId(), incoming.getPoolId()));
            target.setConnectTimeout(incoming.getConnectTimeout());
            target.setReadTimeout(incoming.getReadTimeout());
            target.setWriteTimeout(incoming.getWriteTimeout());
            target.setCallTimeout(incoming.getCallTimeout());
            target.setRetryOnConnectionFailure(incoming.getRetryOnConnectionFailure());
            target.setFollowRedirects(incoming.getFollowRedirects());
            target.setFollowSslRedirects(incoming.getFollowSslRedirects());
            target.setProxyHost(incoming.getProxyHost());
            target.setProxyPort(incoming.getProxyPort());
            target.setProxyType(incoming.getProxyType());
            target.setExposeApi(incoming.getExposeApi());
            target.setExposedPath(incoming.getExposedPath());
            target.setExposedMethod(incoming.getExposedMethod());
            target.setExposedParamType(incoming.getExposedParamType());
            target.setExposedMappingConfig(incoming.getExposedMappingConfig());
            target.setRetryMaxAttempts(incoming.getRetryMaxAttempts());
            target.setRetryOnHttpStatus(incoming.getRetryOnHttpStatus());
            if (existing == null) {
                target.setCreateTime(LocalDateTime.now());
            }
            target.setUpdateTime(LocalDateTime.now());
            httpJimuService.saveOrUpdate(target);
        }
    }

    private String normalizeStepsConfig(String stepsConfig) {
        return (stepsConfig == null || stepsConfig.isBlank()) ? "[]" : stepsConfig;
    }
}
