package com.jimu.http.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jimu.http.dto.script.BeanMetaDetail;
import com.jimu.http.dto.script.ScriptMeta;
import com.jimu.http.engine.HttpJimuEngine;
import com.jimu.http.engine.model.ExecuteDetail;
import com.jimu.http.engine.model.PreviewDetail;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.entity.HttpJimuJobLog;
import com.jimu.http.entity.HttpJimuPool;
import com.jimu.http.entity.HttpJimuStep;
import com.jimu.http.model.Result;
import com.jimu.http.service.HttpJimuJobLogService;
import com.jimu.http.service.HttpJimuPoolService;
import com.jimu.http.service.HttpJimuScriptMetaService;
import com.jimu.http.service.HttpJimuService;
import com.jimu.http.service.HttpJimuStepService;
import com.jimu.http.support.HttpJimuConfigSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/http-jimu-api")
@RequiredArgsConstructor
public class HttpJimuController {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern SIMPLE_KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
    private static final Pattern EXT_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]+");

    private final HttpJimuService httpJimuService;
    private final HttpJimuJobLogService jobLogService;
    private final HttpJimuStepService stepService;
    private final HttpJimuPoolService poolService;
    private final HttpJimuEngine engine;
    private final HttpJimuScriptMetaService scriptMetaService;

    @GetMapping("/pools")
    public Result<List<HttpJimuPool>> listPools() {
        return Result.success(poolService.list());
    }

    @PostMapping("/pools/save")
    public Result<Boolean> savePool(@RequestBody HttpJimuPool pool) {
        try {
            if (pool.getId() == null) {
                pool.setCreateTime(LocalDateTime.now());
            }
            pool.setUpdateTime(LocalDateTime.now());
            boolean success = poolService.saveOrUpdate(pool);
            if (success && pool.getId() != null) {
                engine.evictClientPool(pool.getId());
            }
            return Result.success(success);
        } catch (IllegalArgumentException e) {
            return Result.error(safeErrorMessage(e, "Save pool failed"));
        } catch (Exception e) {
            return Result.error(safeErrorMessage(e, "Save pool failed"));
        }
    }

    @DeleteMapping("/pools/delete/{id}")
    public Result<Boolean> deletePool(@PathVariable("id") String id) {
        boolean success = poolService.removeById(id);
        if (success) {
            engine.evictClientPool(id);
        }
        return Result.success(success);
    }

    @GetMapping("/steps")
    public Result<List<HttpJimuStep>> listSteps() {
        return Result.success(stepService.list());
    }

    @PostMapping("/steps/save")
    public Result<Boolean> saveStep(@RequestBody HttpJimuStep step) {
        try {
            if (step.getId() == null) {
                step.setCreateTime(LocalDateTime.now());
            }
            step.setUpdateTime(LocalDateTime.now());
            boolean success = stepService.saveOrUpdate(step);
            if (success) {
                engine.evictStepsCache();
            }
            return Result.success(success);
        } catch (IllegalArgumentException e) {
            return Result.error(safeErrorMessage(e, "Save step failed"));
        } catch (Exception e) {
            return Result.error(safeErrorMessage(e, "Save step failed"));
        }
    }

    @DeleteMapping("/steps/delete/{id}")
    public Result<Boolean> deleteStep(@PathVariable("id") String id) {
        boolean success = stepService.removeById(id);
        if (success) {
            engine.evictStepsCache();
        }
        return Result.success(success);
    }

    @PostMapping("/validate-script")
    public Result<Map<String, Object>> validateScript(@RequestBody Map<String, String> payload) {
        return scriptMetaService.validateScript(payload);
    }

    @GetMapping("/list")
    public Result<List<HttpJimuConfig>> list() {
        return Result.success(httpJimuService.list());
    }

    @GetMapping("/script-meta")
    public Result<ScriptMeta> scriptMeta() {
        return scriptMetaService.scriptMeta();
    }

    @GetMapping("/script-meta/bean/{beanName}")
    public Result<BeanMetaDetail> beanMeta(@PathVariable("beanName") String beanName) {
        return scriptMetaService.beanMeta(beanName);
    }

    @PostMapping("/script-meta/cache/evict")
    public Result<Boolean> evictScriptMetaCache() {
        return scriptMetaService.evictScriptMetaCache();
    }

    @GetMapping("/job-logs/{configId}")
    public Result<List<HttpJimuJobLog>> getJobLogs(@PathVariable("configId") String configId) {
        Page<HttpJimuJobLog> page = new Page<>(1, 100, false);
        return Result.success(jobLogService.page(page, new LambdaQueryWrapper<HttpJimuJobLog>()
                .eq(HttpJimuJobLog::getConfigId, configId)
                .orderByDesc(HttpJimuJobLog::getCreateTime)).getRecords());
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody HttpJimuConfig config) {
        try {
            String cronError = HttpJimuConfigSupport.validateCronConfig(config);
            if (cronError != null) {
                return Result.error(cronError);
            }
            String methodError = HttpJimuConfigSupport.validateAndNormalizeMethod(config);
            if (methodError != null) {
                return Result.error(methodError);
            }
            String placeholderError = validateConfigPlaceholders(config);
            if (placeholderError != null) {
                return Result.error(placeholderError);
            }
            if (config.getCronConfig() != null) {
                config.setCronConfig(config.getCronConfig().trim());
            }
            if (config.getId() == null) {
                config.setCreateTime(LocalDateTime.now());
            }
            config.setUpdateTime(LocalDateTime.now());
            return Result.success(httpJimuService.saveOrUpdate(config));
        } catch (IllegalArgumentException e) {
            return Result.error(safeErrorMessage(e, "Save config failed"));
        } catch (Exception e) {
            return Result.error(safeErrorMessage(e, "Save config failed"));
        }
    }

    @PostMapping("/preview-call/{httpId}")
    public Result<PreviewDetail> previewCall(@PathVariable("httpId") String httpId,
                                             @RequestBody Map<String, Object> params) {
        try {
            return Result.success(httpJimuService.preview(httpId, params));
        } catch (Exception e) {
            return Result.error(safeErrorMessage(e, "Preview call failed"));
        }
    }

    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable("id") String id) {
        return Result.success(httpJimuService.removeById(id));
    }

    @PostMapping("/test-call/{httpId}")
    public Result<ExecuteDetail> testCall(@PathVariable("httpId") String httpId, @RequestBody Map<String, Object> params) {
        try {
            ExecuteDetail result = httpJimuService.callWithDetail(httpId, params);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(safeErrorMessage(e, "Test call failed"));
        }
    }

    private String validateConfigPlaceholders(HttpJimuConfig config) {
        if (config == null) {
            return "Config cannot be null";
        }
        List<String> rawFields = new ArrayList<>();
        rawFields.add(config.getUrl());
        rawFields.add(config.getHeaders());
        rawFields.add(config.getQueryParams());
        rawFields.add(config.getBodyConfig());
        rawFields.add(config.getStepsConfig());
        rawFields.add(config.getParamsConfig());

        Set<String> unsupported = new HashSet<>();
        for (String raw : rawFields) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            int idx = raw.indexOf("${");
            while (idx >= 0) {
                int right = raw.indexOf("}", idx + 2);
                if (right < 0) {
                    return "Invalid placeholder format: found unclosed ${...}";
                }
                idx = raw.indexOf("${", right + 1);
            }
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(raw);
            while (matcher.find()) {
                String key = matcher.group(1) != null ? matcher.group(1).trim() : "";
                if (key.isEmpty()) {
                    return "Invalid placeholder: ${} is not allowed";
                }
                if (key.startsWith("env:")) {
                    String sub = key.substring(4);
                    if (sub.isBlank() || !EXT_KEY_PATTERN.matcher(sub).matches()) {
                        return "Invalid placeholder: " + matcher.group(0);
                    }
                    continue;
                }
                if (key.startsWith("redis:")) {
                    String sub = key.substring(6);
                    if (sub.isBlank() || !EXT_KEY_PATTERN.matcher(sub).matches()) {
                        return "Invalid placeholder: " + matcher.group(0);
                    }
                    continue;
                }
                if (!SIMPLE_KEY_PATTERN.matcher(key).matches()) {
                    unsupported.add(matcher.group(0));
                }
            }
        }
        if (!unsupported.isEmpty()) {
            return "Unsupported placeholders: " + String.join(", ", unsupported);
        }
        return null;
    }

    private String safeErrorMessage(Exception e, String fallback) {
        if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
            return fallback;
        }
        return e.getMessage();
    }
}
