package com.jimu.http.engine;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jimu.http.cache.JimuCacheProvider;
import com.jimu.http.cache.MemoryJimuCacheProvider;
import com.jimu.http.config.JimuProperties;
import com.jimu.http.engine.model.ExecuteDetail;
import com.jimu.http.engine.model.PreparedExecution;
import com.jimu.http.engine.model.PreviewDetail;
import com.jimu.http.engine.model.StepTrace;
import com.jimu.http.engine.step.StepContext;
import com.jimu.http.engine.step.StepProcessor;
import com.jimu.http.engine.support.HttpJimuTransportSupport;
import com.jimu.http.engine.support.JimuExpressionResolver;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.entity.HttpJimuPool;
import com.jimu.http.entity.HttpJimuStep;
import com.jimu.http.model.HttpStep;
import com.jimu.http.model.enums.StepTarget;
import com.jimu.http.model.enums.StepType;
import com.jimu.http.service.HttpJimuPoolService;
import com.jimu.http.service.HttpJimuStepService;
import com.jimu.http.support.HttpJimuConfigSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(JimuProperties.class)
public class HttpJimuEngine {

    @Autowired
    private JimuExpressionResolver expressionResolver;

    @Autowired
    private HttpJimuStepService stepService;

    @Autowired
    private HttpJimuTransportSupport transportSupport;

    @Autowired(required = false)
    private HttpJimuPoolService poolService;

    private final JimuProperties jimuProperties;

    private final Map<StepType, StepProcessor> processorMap = new EnumMap<>(StepType.class);
    private static final Type STEPS_LIST_TYPE = new TypeReference<List<HttpStep>>() {}.getType();
    private static final String CACHE_NAME_STEPS = "steps_config";
    private final JimuCacheProvider fallbackCacheProvider = new MemoryJimuCacheProvider();

    @Autowired(required = false)
    private JimuCacheProvider cacheProvider;

    @Autowired
    public void setStepProcessors(List<StepProcessor> processors) {
        if (processors == null) {
            return;
        }
        for (StepProcessor processor : processors) {
            processorMap.put(processor.getType(), processor);
        }
    }

    public String execute(HttpJimuConfig config, Map<String, Object> inputParams) {
        return executeWithDetail(config, inputParams).getResponseBody();
    }

    public ExecuteDetail executeWithDetail(HttpJimuConfig config, Map<String, Object> inputParams) {
        String methodError = HttpJimuConfigSupport.validateAndNormalizeMethod(config);
        if (methodError != null) {
            throw new IllegalArgumentException(methodError);
        }
        PreparedExecution prepared = prepareExecution(config, inputParams, true);

        int configuredRetryAttempts = resolveRetryMaxAttempts(config);
        String configuredRetryStatuses = resolveRetryOnHttpStatus(config);
        int maxAttempts = configuredRetryAttempts > 0 ? configuredRetryAttempts + 1 : 1;
        Set<Integer> retryStatusCodes = parseRetryStatusCodes(configuredRetryStatuses);

        ExecuteDetail detail = null;
        int attempt = 0;
        while (attempt < maxAttempts) {
            attempt++;
            detail = transportSupport.sendRequestWithDetail(
                    config, prepared.getMethod(), prepared.getUrl(), prepared.getHeaders(), prepared.getBody());
            if (attempt < maxAttempts && retryStatusCodes.contains(detail.getResponseStatus())) {
                log.warn("Retrying HTTP call [{}] due to status {}, attempt {}/{}",
                        config.getHttpId(), detail.getResponseStatus(), attempt, maxAttempts - 1);
                continue;
            }
            break;
        }
        applyResponseSteps(detail, prepared.getResolvedSteps(), prepared.getContext(), prepared.getStepTraces());
        detail.setStepTraces(prepared.getStepTraces());
        return detail;
    }

    private Set<Integer> parseRetryStatusCodes(String retryOnHttpStatus) {
        Set<Integer> codes = new java.util.LinkedHashSet<>();
        if (retryOnHttpStatus == null || retryOnHttpStatus.isBlank()) {
            return codes;
        }
        for (String s : retryOnHttpStatus.split(",")) {
            try {
                codes.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignore) {
                log.warn("Invalid retryOnHttpStatus value: {}", s.trim());
            }
        }
        return codes;
    }

    private int resolveRetryMaxAttempts(HttpJimuConfig config) {
        if (config.getRetryMaxAttempts() != null) {
            return config.getRetryMaxAttempts();
        }
        HttpJimuPool pool = resolvePool(config);
        return pool != null && pool.getRetryMaxAttempts() != null ? pool.getRetryMaxAttempts() : 0;
    }

    private String resolveRetryOnHttpStatus(HttpJimuConfig config) {
        if (StrUtil.isNotBlank(config.getRetryOnHttpStatus())) {
            return config.getRetryOnHttpStatus();
        }
        HttpJimuPool pool = resolvePool(config);
        return pool != null ? pool.getRetryOnHttpStatus() : null;
    }

    private HttpJimuPool resolvePool(HttpJimuConfig config) {
        if (poolService == null || config == null || StrUtil.isBlank(config.getPoolId())) {
            return null;
        }
        return poolService.getById(config.getPoolId());
    }

    public PreviewDetail previewWithSteps(HttpJimuConfig config, Map<String, Object> inputParams) {
        String methodError = HttpJimuConfigSupport.validateAndNormalizeMethod(config);
        if (methodError != null) {
            throw new IllegalArgumentException(methodError);
        }
        PreparedExecution prepared = prepareExecution(config, inputParams, true);
        PreviewDetail detail = new PreviewDetail();
        detail.setRequestMethod(prepared.getMethod());
        detail.setRequestUrl(prepared.getUrl());
        detail.setRequestHeaders(prepared.getHeaders());
        detail.setRequestBody(stringifyBody(prepared.getBody()));
        detail.setStepTraces(prepared.getStepTraces());
        return detail;
    }

    private PreparedExecution prepareExecution(HttpJimuConfig config, Map<String, Object> inputParams, boolean includeStepTraces) {
        Map<String, Object> safeInputParams = inputParams != null ? inputParams : Collections.emptyMap();
        log.info("Executing HTTP Jimu [{}], inputKeys: {}", config.getHttpId(), safeInputParams.keySet());

        Map<String, Object> context = HttpJimuConfigSupport.buildContext(safeInputParams, config.getParamsConfig());
        String finalUrl = expressionResolver.resolve(config.getUrl(), context);
        Map<String, String> queryParams = parseKvConfig(config.getQueryParams(), context);
        Map<String, String> headers = parseKvConfig(config.getHeaders(), context);
        Object body = initializeBody(config, context);
        List<HttpStep> steps = resolveSteps(config.getStepsConfig());

        List<StepTrace> traces = includeStepTraces ? new ArrayList<>() : Collections.emptyList();
        int stepIndex = 0;
        for (HttpStep step : steps) {
            stepIndex++;
            StepTarget target = StepTarget.fromCode(step.getTarget());
            if (target.isResponse()) {
                continue;
            }

            Map<String, Object> stepConfig = step.getConfig() != null ? new HashMap<>(step.getConfig()) : new HashMap<>();
            step.setConfig(stepConfig);

            Object inputTarget = switch (target) {
                case BODY -> body;
                case HEADER -> headers;
                case QUERY -> queryParams;
                case FORM -> body instanceof Map ? body : new HashMap<>();
                default -> body;
            };

            String inputSnapshot = (includeStepTraces || step.isEnableLog()) ? stringifyBody(inputTarget) : null;
            StepContext stepContext = StepContext.builder()
                    .context(context)
                    .url(finalUrl)
                    .headers(headers)
                    .queryParams(queryParams)
                    .build();
            // FIX (Issue 4): unified invocation via shared helper
            Object outputTarget = invokeStep(step, inputTarget, stepConfig, stepContext,
                    includeStepTraces, "REQUEST", stepIndex, traces);

            if (step.isEnableLog()) {
                log.info("Step [{}] ({}) Log - Input: {}, Output: {}", step.getType(), step.getTarget(), inputSnapshot, stringifyBody(outputTarget));
            }

            switch (target) {
                case BODY -> body = outputTarget;
                case HEADER -> headers = ensureStringMap(outputTarget, "HEADER", stepIndex);
                case QUERY -> queryParams = ensureStringMap(outputTarget, "QUERY", stepIndex);
                case FORM -> {
                    if (body instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<Object, Object> bodyMap = (Map<Object, Object>) map;
                        bodyMap.putAll(ensureObjectMap(outputTarget, "FORM", stepIndex));
                    } else {
                        body = outputTarget;
                    }
                }
                default -> {
                    if (body instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<Object, Object> bodyMap = (Map<Object, Object>) map;
                        bodyMap.putAll(ensureObjectMap(outputTarget, "BODY", stepIndex));
                    } else {
                        body = outputTarget;
                    }
                }
            }
        }

        finalUrl = transportSupport.mergeUrlQueryParams(finalUrl, queryParams);

        PreparedExecution prepared = new PreparedExecution();
        prepared.setMethod(config.getMethod());
        prepared.setUrl(finalUrl);
        prepared.setHeaders(headers);
        prepared.setBody(body);
        prepared.setContext(context);
        prepared.setResolvedSteps(steps);
        prepared.setStepTraces(traces);
        return prepared;
    }

    private String stringifyBody(Object body) {
        if (body == null) {
            return "";
        }
        if (body instanceof String s) {
            return s;
        }
        try {
            return JSON.toJSONString(body);
        } catch (Exception e) {
            return String.valueOf(body);
        }
    }

    /**
     * FIX (Issue 4): Shared step-invocation helper to eliminate duplication between
     * prepareExecution (request phase) and applyResponseSteps (response phase).
     * Invokes the correct StepProcessor and optionally records a StepTrace.
     */
    private Object invokeStep(HttpStep step, Object inputTarget,
                              Map<String, Object> stepConfig, StepContext stepContext,
                              boolean recordTrace, String phase, int stepIndex,
                              List<StepTrace> traces) {
        String inputSnapshot = recordTrace ? stringifyBody(inputTarget) : null;
        Object outputTarget = inputTarget;

        StepType stepType = StepType.fromCode(step.getType());
        StepProcessor processor = processorMap.get(stepType);
        if (processor != null) {
            outputTarget = processor.process(inputTarget, stepConfig, stepContext);
        } else {
            log.warn("Unknown step type: {} (phase={})", step.getType(), phase);
        }

        if (recordTrace && traces != null) {
            StepTrace trace = new StepTrace();
            trace.setStepIndex(stepIndex);
            trace.setPhase(phase);
            trace.setStepType(step.getType());
            trace.setTarget(step.getTarget());
            trace.setInputSnapshot(inputSnapshot);
            trace.setOutputSnapshot(stringifyBody(outputTarget));
            traces.add(trace);
        }
        return outputTarget;
    }

    private List<HttpStep> resolveSteps(String stepsConfig) {
        if (StrUtil.isBlank(stepsConfig)) {
            return new ArrayList<>();
        }

        String cacheKey = SecureUtil.md5(stepsConfig);
        List<HttpStep> cached = cache().get(CACHE_NAME_STEPS, cacheKey, STEPS_LIST_TYPE);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        List<HttpStep> resolvedSteps = resolveStepsFromConfig(stepsConfig);
        cache().put(CACHE_NAME_STEPS, cacheKey, resolvedSteps, jimuProperties.getCache().getStepsTtlMs());
        return new ArrayList<>(resolvedSteps);
    }

    private List<HttpStep> resolveStepsFromConfig(String stepsConfig) {
        List<HttpStep> steps = JSON.parseArray(stepsConfig, HttpStep.class);
        if (steps == null) {
            return new ArrayList<>();
        }
        Set<String> stepCodes = new LinkedHashSet<>();
        for (HttpStep step : steps) {
            if (step != null && StrUtil.isNotBlank(step.getStepCode())) {
                stepCodes.add(step.getStepCode());
            }
        }

        Map<String, HttpJimuStep> libraryStepMap = new HashMap<>();
        if (!stepCodes.isEmpty()) {
            List<HttpJimuStep> librarySteps = stepService.list(new LambdaQueryWrapper<HttpJimuStep>()
                    .in(HttpJimuStep::getCode, stepCodes));
            if (librarySteps != null) {
                for (HttpJimuStep libraryStep : librarySteps) {
                    if (libraryStep != null && StrUtil.isNotBlank(libraryStep.getCode())) {
                        libraryStepMap.put(libraryStep.getCode(), libraryStep);
                    }
                }
            }
        }

        for (HttpStep step : steps) {
            if (step == null) {
                continue;
            }
            if (StrUtil.isNotBlank(step.getStepCode())) {
                HttpJimuStep libraryStep = libraryStepMap.get(step.getStepCode());
                if (libraryStep != null) {
                    step.setType(libraryStep.getType());
                    step.setTarget(libraryStep.getTarget());
                    Map<String, Object> libraryConfig = new HashMap<>();
                    if (StrUtil.isNotBlank(libraryStep.getConfigJson())) {
                        try {
                            libraryConfig = JSON.parseObject(libraryStep.getConfigJson(), Map.class);
                        } catch (Exception e) {
                            log.warn("Failed to parse library step config", e);
                        }
                    }
                    if ("SCRIPT".equals(step.getType())) {
                        libraryConfig.put("script", libraryStep.getScriptContent());
                    }
                    if (step.getConfig() != null) {
                        libraryConfig.putAll(step.getConfig());
                    }
                    step.setConfig(libraryConfig);
                }
            }
            if (step.getConfig() == null) {
                step.setConfig(new HashMap<>());
            }
        }
        return steps;
    }

    private void applyResponseSteps(ExecuteDetail detail, List<HttpStep> steps, Map<String, Object> context, List<StepTrace> traces) {
        if (detail == null || steps == null || steps.isEmpty()) {
            return;
        }
        Object responseBody = parseMaybeJson(detail.getResponseBody());
        Map<String, String> responseHeaders = detail.getResponseHeaders() != null
                ? new LinkedHashMap<>(detail.getResponseHeaders()) : new LinkedHashMap<>();
        Map<String, Object> responseStatus = new LinkedHashMap<>();
        responseStatus.put("status", detail.getResponseStatus());

        int stepIndex = 0;
        for (HttpStep step : steps) {
            stepIndex++;
            StepTarget target = StepTarget.fromCode(step.getTarget());
            if (!target.isResponse()) {
                continue;
            }

            Map<String, Object> stepConfig = step.getConfig() != null ? new HashMap<>(step.getConfig()) : new HashMap<>();
            Object inputTarget = target == StepTarget.RESPONSE_BODY
                    ? responseBody
                    : target == StepTarget.RESPONSE_HEADER ? responseHeaders : responseStatus;
            StepContext stepContext = StepContext.builder()
                    .context(context)
                    .url(detail.getRequestUrl())
                    .headers(detail.getRequestHeaders())
                    .queryParams(new LinkedHashMap<>())
                    .build();
            // FIX (Issue 4): unified invocation via shared helper
            Object outputTarget = invokeStep(step, inputTarget, stepConfig, stepContext,
                    traces != null, "RESPONSE", stepIndex, traces);

            if (target == StepTarget.RESPONSE_BODY) {
                responseBody = outputTarget;
            } else if (target == StepTarget.RESPONSE_HEADER) {
                responseHeaders = ensureStringMap(outputTarget, "RESPONSE_HEADER", stepIndex);
            } else {
                responseStatus = ensureObjectMap(outputTarget, "RESPONSE_STATUS", stepIndex);
            }
        }

        detail.setResponseBody(stringifyBody(responseBody));
        detail.setResponseHeaders(responseHeaders);
        Object finalStatus = responseStatus.get("status");
        if (finalStatus != null) {
            try {
                detail.setResponseStatus(Integer.parseInt(String.valueOf(finalStatus)));
            } catch (Exception ignore) {
                // keep original status
            }
        }
    }

    private Object parseMaybeJson(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        try {
            if (trimmed.startsWith("{")) {
                return JSON.parseObject(trimmed, Map.class);
            }
            if (trimmed.startsWith("[")) {
                return JSON.parseArray(trimmed);
            }
        } catch (Exception ignore) {
            // fallback to plain string
        }
        return text;
    }

    private Object initializeBody(HttpJimuConfig config, Map<String, Object> context) {
        String bodyType = config.getBodyType();
        String bodyConfig = config.getBodyConfig();

        if (StrUtil.equals(bodyType, "none")) {
            return null;
        }
        if (StrUtil.isBlank(bodyConfig)) {
            return new HashMap<>(context);
        }

        if (StrUtil.equals(bodyType, "form-data") || StrUtil.equals(bodyType, "x-www-form-urlencoded")) {
            return parseKvConfig(bodyConfig, context);
        }

        // --- FIX (Issue 3): Safe placeholder resolution in JSON body ---
        // Instead of raw String.replaceAll on the whole JSON (which breaks format when
        // values contain quotes, newlines, etc.), we parse to an object tree first,
        // then resolve placeholders on each leaf String value individually.
        String trimmed = bodyConfig.trim();
        try {
            if (trimmed.startsWith("{")) {
                Map<Object, Object> tree = JSON.parseObject(trimmed, Map.class);
                return resolveTreePlaceholders(tree, context);
            } else if (trimmed.startsWith("[")) {
                JSONArray arr = JSON.parseArray(trimmed);
                return resolveTreePlaceholders(arr, context);
            }
        } catch (Exception e) {
            log.warn("Failed to parse bodyConfig as JSON, falling back to plain string resolve: {}", e.getMessage());
        }
        // Plain-text body: safe to resolve as a whole string
        return expressionResolver.resolve(bodyConfig, context);
    }

    /**
     * Recursively walks a JSON tree (Map / List / String) and resolves
     * ${...} placeholders only on leaf String values, preserving JSON structure.
     */
    @SuppressWarnings("unchecked")
    private Object resolveTreePlaceholders(Object node, Map<String, Object> context) {
        if (node instanceof Map<?, ?> map) {
            Map<Object, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(entry.getKey(), resolveTreePlaceholders(entry.getValue(), context));
            }
            return result;
        } else if (node instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(resolveTreePlaceholders(item, context));
            }
            return result;
        } else if (node instanceof String s) {
            return expressionResolver.resolve(s, context);
        }
        return node; // Number, Boolean, null – leave as-is
    }

    private Map<String, String> parseKvConfig(String configJson, Map<String, Object> context) {
        Map<String, String> result = new LinkedHashMap<>();
        if (StrUtil.isBlank(configJson)) {
            return result;
        }

        try {
            JSONArray array = JSON.parseArray(configJson);
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getJSONObject(i);
                String key = item.getString("key");
                String value = item.getString("value");
                if (StrUtil.isNotBlank(key)) {
                    result.put(key, expressionResolver.resolve(value, context));
                }
            }
        } catch (Exception e) {
            try {
                JSONObject json = JSON.parseObject(configJson);
                for (String key : json.keySet()) {
                    result.put(key, expressionResolver.resolve(json.getString(key), context));
                }
            } catch (Exception e2) {
                log.error("Parse KV config error: {}", configJson, e2);
            }
        }
        return result;
    }

    private Map<String, String> ensureStringMap(Object target, String targetName, int stepIndex) {
        Map<String, Object> map = ensureObjectMap(target, targetName, stepIndex);
        Map<String, String> stringMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            stringMap.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return stringMap;
    }

    private Map<String, Object> ensureObjectMap(Object target, String targetName, int stepIndex) {
        if (!(target instanceof Map<?, ?> rawMap)) {
            throw new IllegalStateException("Step " + stepIndex + " output for " + targetName + " must be a Map");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return map;
    }

    public void evictClientPool(String poolId) {
        transportSupport.evictClientPool(poolId);
    }

    public void evictStepsCache() {
        cache().clear(CACHE_NAME_STEPS);
    }

    private JimuCacheProvider cache() {
        return cacheProvider != null ? cacheProvider : fallbackCacheProvider;
    }
}
