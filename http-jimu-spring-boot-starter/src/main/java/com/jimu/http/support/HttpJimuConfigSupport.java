package com.jimu.http.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jimu.http.entity.HttpJimuConfig;
import org.springframework.scheduling.support.CronExpression;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HttpJimuConfigSupport {

    private static final Set<String> ALLOWED_HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"
    );

    private HttpJimuConfigSupport() {
    }

    public static String validateCronConfig(HttpJimuConfig config) {
        if (config == null) {
            return "Config cannot be null";
        }
        if (!Boolean.TRUE.equals(config.getEnableJob())) {
            return null;
        }
        String cron = config.getCronConfig() != null ? config.getCronConfig().trim() : "";
        if (cron.isEmpty()) {
            return "Cron cannot be empty when job is enabled";
        }
        if (!CronExpression.isValidExpression(cron)) {
            return "Invalid Cron expression: " + cron;
        }
        return null;
    }

    public static String validateAndNormalizeMethod(HttpJimuConfig config) {
        if (config == null) {
            return "Config cannot be null";
        }
        if (StrUtil.isBlank(config.getMethod())) {
            config.setMethod("POST");
            return null;
        }
        String normalized = config.getMethod().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_HTTP_METHODS.contains(normalized)) {
            return "Unsupported HTTP method: " + config.getMethod();
        }
        config.setMethod(normalized);
        return null;
    }

    public static Map<String, Object> buildContext(Map<String, Object> inputParams, String paramsConfig) {
        Map<String, Object> input = inputParams != null ? inputParams : Map.of();
        Map<String, Object> context = new HashMap<>(input);
        if (StrUtil.isBlank(paramsConfig)) {
            return context;
        }

        String trimmed = paramsConfig.trim();
        if (trimmed.startsWith("[")) {
            applyArrayConfig(context, input, trimmed);
            return context;
        }
        if (trimmed.startsWith("{")) {
            applyObjectConfig(context, input, trimmed);
            return context;
        }
        throw new IllegalArgumentException("paramsConfig must be a JSON object or JSON array");
    }

    private static void applyArrayConfig(Map<String, Object> context, Map<String, Object> input, String json) {
        JSONArray arr = JSON.parseArray(json);
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.size(); i++) {
            Object raw = arr.get(i);
            if (!(raw instanceof JSONObject)) {
                continue;
            }
            JSONObject item = (JSONObject) raw;
            String key = firstNonBlank(item.getString("key"), item.getString("name"), item.getString("paramKey"));
            if (StrUtil.isBlank(key)) {
                continue;
            }
            String source = firstNonBlank(item.getString("source"), item.getString("sourceKey"), item.getString("from"), key);
            Object value = resolveValueByRule(input, key, source, item);
            boolean required = asBoolean(item.get("required")) || asBoolean(item.get("require"));
            if (required && isEmpty(value)) {
                throw new IllegalArgumentException("Missing required param: " + key);
            }
            if (!isEmpty(value) || item.containsKey("defaultValue") || item.containsKey("default")
                    || input.containsKey(source) || input.containsKey(key)) {
                context.put(key, value);
            }
        }
    }

    private static void applyObjectConfig(Map<String, Object> context, Map<String, Object> input, String json) {
        JSONObject obj = JSON.parseObject(json);
        if (obj == null) {
            return;
        }
        for (String key : obj.keySet()) {
            Object raw = obj.get(key);
            if (raw instanceof JSONObject spec) {
                Object value = resolveValueByRule(input, key,
                        firstNonBlank(spec.getString("source"), spec.getString("sourceKey"), spec.getString("from"), key),
                        spec);
                boolean required = asBoolean(spec.get("required")) || asBoolean(spec.get("require"));
                if (required && isEmpty(value)) {
                    throw new IllegalArgumentException("Missing required param: " + key);
                }
                if (!isEmpty(value) || spec.containsKey("defaultValue") || spec.containsKey("default") || input.containsKey(key)) {
                    context.put(key, value);
                }
                continue;
            }

            Object existing = context.get(key);
            if (isEmpty(existing)) {
                context.put(key, raw);
            }
        }
    }

    private static Object resolveValueByRule(Map<String, Object> input, String key, String source, JSONObject spec) {
        Object value = null;
        if (input.containsKey(source)) {
            value = input.get(source);
        } else if (input.containsKey(key)) {
            value = input.get(key);
        }
        if (isEmpty(value)) {
            if (spec.containsKey("defaultValue")) {
                value = spec.get("defaultValue");
            } else if (spec.containsKey("default")) {
                value = spec.get("default");
            }
        }
        return value;
    }

    private static boolean asBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        return false;
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String item : candidates) {
            if (item != null && !item.isBlank()) {
                return item.trim();
            }
        }
        return null;
    }
}
