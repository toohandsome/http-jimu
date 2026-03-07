package com.jimu.http.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HttpJimuExposedRequestResolver {

    public static final String INJECT_QUERY_KEY = "_jimuInjectedQuery";
    public static final String INJECT_HEADER_KEY = "_jimuInjectedHeader";
    public static final String INJECT_BODY_KEY = "_jimuInjectedBody";
    public static final String INJECT_FORM_KEY = "_jimuInjectedForm";
    public static final String INJECT_RAW_KEY = "_jimuInjectedRaw";
    private static final Set<String> AUTO_HEADER_BLACKLIST = new HashSet<>(List.of(
            "host", "content-length", "connection", "transfer-encoding"
    ));

    private HttpJimuExposedRequestResolver() {
    }

    public static Map<String, Object> resolve(HttpServletRequest request, String paramType, String mappingConfig) throws IOException {
        ResolvedInbound inbound = extractInbound(request);
        String normalizedType = normalizeParamType(paramType);
        if ("CUSTOM".equals(normalizedType)) {
            return applyCustomMappings(inbound, mappingConfig);
        }
        return inbound.autoInput();
    }

    private static Map<String, Object> applyCustomMappings(ResolvedInbound inbound, String mappingConfig) {
        Map<String, Object> input = new LinkedHashMap<>(inbound.rawInput());
        JSONArray mappings = parseMappings(mappingConfig);
        if (mappings == null || mappings.isEmpty()) {
            return input;
        }
        Map<String, Object> injectedQuery = new LinkedHashMap<>();
        Map<String, Object> injectedHeader = new LinkedHashMap<>();
        Map<String, Object> injectedBody = new LinkedHashMap<>();
        Map<String, Object> injectedForm = new LinkedHashMap<>();
        String injectedRaw = null;

        for (int i = 0; i < mappings.size(); i++) {
            JSONObject item = mappings.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String sourceType = upper(item.getString("sourceType"));
            String targetType = upper(item.getString("targetType"));
            if (sourceType.isBlank() || targetType.isBlank()) {
                continue;
            }
            if ("RAW".equals(sourceType) || "RAW".equals(targetType)) {
                if (!"RAW".equals(sourceType) || !"RAW".equals(targetType)) {
                    throw new IllegalArgumentException("RAW mappings only support RAW -> RAW");
                }
                if (!isEmpty(inbound.rawBody())) {
                    injectedRaw = inbound.rawBody();
                }
                continue;
            }

            Map<String, Object> sourceValues = resolveSourceValues(inbound, sourceType);
            if (sourceValues.isEmpty()) {
                continue;
            }
            switch (targetType) {
                case "QUERY" -> injectedQuery.putAll(sourceValues);
                case "HEADER" -> injectedHeader.putAll(sourceValues);
                case "BODY", "JSON" -> mergeInto(injectedBody, sourceValues);
                case "FORM" -> injectedForm.putAll(sourceValues);
                case "PATH" -> sourceValues.forEach((key, value) -> input.put("path." + key, value));
                default -> input.putAll(sourceValues);
            }
        }
        if (!injectedQuery.isEmpty()) {
            input.put(INJECT_QUERY_KEY, injectedQuery);
        }
        if (!injectedHeader.isEmpty()) {
            input.put(INJECT_HEADER_KEY, injectedHeader);
        }
        if (!injectedBody.isEmpty()) {
            input.put(INJECT_BODY_KEY, injectedBody);
        }
        if (!injectedForm.isEmpty()) {
            input.put(INJECT_FORM_KEY, injectedForm);
        }
        if (!isEmpty(injectedRaw)) {
            input.put(INJECT_RAW_KEY, injectedRaw);
        }
        return input;
    }

    private static JSONArray parseMappings(String mappingConfig) {
        if (mappingConfig == null || mappingConfig.isBlank()) {
            return null;
        }
        return JSON.parseArray(mappingConfig);
    }

    private static Map<String, Object> resolveSourceValues(ResolvedInbound inbound, String sourceType) {
        return switch (sourceType) {
            case "PATH" -> inbound.path();
            case "QUERY" -> inbound.query();
            case "HEADER" -> inbound.header();
            case "BODY", "JSON" -> inbound.body() != null ? inbound.body() : Collections.emptyMap();
            case "FORM" -> inbound.form();
            default -> Collections.emptyMap();
        };
    }

    private static ResolvedInbound extractInbound(HttpServletRequest request) throws IOException {
        Map<String, Object> rawInput = new LinkedHashMap<>();
        rawInput.put("requestMethod", request.getMethod());
        rawInput.put("requestUri", request.getRequestURI());
        rawInput.put("queryString", request.getQueryString());

        Map<String, Object> headers = extractHeaders(request);
        rawInput.put("headers", headers);
        flattenInto(rawInput, "header", headers);

        Map<String, Object> pathVariables = extractPathVariables(request);
        rawInput.put("path", pathVariables);
        flattenInto(rawInput, "path", pathVariables);

        Map<String, Object> queryParams = parseKvString(request.getQueryString(), charset(request));
        rawInput.put("query", queryParams);
        flattenInto(rawInput, "query", queryParams);

        String rawBody = readBody(request);
        if (!rawBody.isBlank()) {
            rawInput.put("rawBody", rawBody);
        }

        Map<String, Object> formParams = extractFormParams(request, rawBody);
        if (!formParams.isEmpty()) {
            rawInput.put("form", formParams);
            flattenInto(rawInput, "form", formParams);
        }

        Map<String, Object> body = parseBody(rawBody);
        if (body != null && !body.isEmpty()) {
            rawInput.put("body", body);
            flattenInto(rawInput, "body", body);
        }

        Map<String, Object> autoInput = new LinkedHashMap<>(rawInput);
        mergeTopLevel(autoInput, pathVariables);
        mergeTopLevel(autoInput, queryParams);
        mergeTopLevel(autoInput, formParams);
        if (body != null) {
            mergeTopLevel(autoInput, body);
        } else if (!rawBody.isBlank()) {
            autoInput.put("body", rawBody);
        }
        if (!queryParams.isEmpty()) {
            autoInput.put(INJECT_QUERY_KEY, new LinkedHashMap<>(queryParams));
        }
        Map<String, Object> forwardedHeaders = sanitizeAutoHeaders(headers);
        if (!forwardedHeaders.isEmpty()) {
            autoInput.put(INJECT_HEADER_KEY, forwardedHeaders);
        }
        if (body != null && !body.isEmpty()) {
            autoInput.put(INJECT_BODY_KEY, new LinkedHashMap<>(body));
        }
        if (!formParams.isEmpty()) {
            autoInput.put(INJECT_FORM_KEY, new LinkedHashMap<>(formParams));
        }
        return new ResolvedInbound(rawInput, autoInput, pathVariables, queryParams, headers, body, formParams, rawBody);
    }

    private static Map<String, Object> sanitizeAutoHeaders(Map<String, Object> headers) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        headers.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            if (AUTO_HEADER_BLACKLIST.contains(normalized)) {
                return;
            }
            sanitized.put(key, value);
        });
        return sanitized;
    }

    private static String normalizeParamType(String paramType) {
        if (paramType == null || paramType.isBlank()) {
            return "AUTO";
        }
        return upper(paramType);
    }

    private static String upper(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
    }

    private static String trim(String text) {
        return text == null ? "" : text.trim();
    }

    private static Charset charset(HttpServletRequest request) {
        String encoding = request.getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception ignore) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String readBody(HttpServletRequest request) throws IOException {
        byte[] bytes = request.getInputStream().readAllBytes();
        if (bytes.length == 0) {
            return "";
        }
        return new String(bytes, charset(request));
    }

    private static Map<String, Object> extractHeaders(HttpServletRequest request) {
        Map<String, Object> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return headers;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            List<String> values = Collections.list(request.getHeaders(name));
            if (values.isEmpty()) {
                continue;
            }
            headers.put(name, values.size() == 1 ? values.get(0) : values);
        }
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractPathVariables(HttpServletRequest request) {
        Object attr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attr instanceof Map<?, ?> map) {
            Map<String, Object> pathVariables = new LinkedHashMap<>();
            map.forEach((key, value) -> pathVariables.put(String.valueOf(key), value));
            return pathVariables;
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> extractFormParams(HttpServletRequest request, String rawBody) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return new LinkedHashMap<>();
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("application/x-www-form-urlencoded")) {
            return parseKvString(rawBody, charset(request));
        }
        if (normalized.startsWith("multipart/form-data")) {
            Map<String, Object> result = new LinkedHashMap<>();
            request.getParameterMap().forEach((key, values) -> putValue(result, key, values));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> parseBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            Object parsed = JSON.parse(rawBody);
            if (parsed instanceof JSONObject jsonObject) {
                Map<String, Object> bodyMap = new LinkedHashMap<>();
                jsonObject.forEach(bodyMap::put);
                return bodyMap;
            }
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> bodyMap = new LinkedHashMap<>();
                map.forEach((key, value) -> bodyMap.put(String.valueOf(key), value));
                return bodyMap;
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    private static Map<String, Object> parseKvString(String raw, Charset charset) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            String decodedKey = UriUtils.decode(key, charset);
            String decodedValue = UriUtils.decode(value, charset);
            putValue(values, decodedKey, new String[]{decodedValue});
        }
        return values;
    }

    private static void putValue(Map<String, Object> target, String key, String[] values) {
        if (key == null || key.isBlank() || values == null || values.length == 0) {
            return;
        }
        if (values.length == 1) {
            target.put(key, values[0]);
            return;
        }
        List<String> list = new ArrayList<>(values.length);
        Collections.addAll(list, values);
        target.put(key, list);
    }

    private static void mergeTopLevel(Map<String, Object> target, Map<String, Object> source) {
        source.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                target.put(key, value);
            }
        });
    }

    private static void flattenInto(Map<String, Object> target, String prefix, Map<String, Object> source) {
        source.forEach((key, value) -> flattenInto(target, prefix + "." + key, value));
    }

    @SuppressWarnings("unchecked")
    private static void flattenInto(Map<String, Object> target, String key, Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((childKey, childValue) ->
                    flattenInto(target, key + "." + String.valueOf(childKey), childValue));
            return;
        }
        target.put(key, value);
    }

    @SuppressWarnings("unchecked")
    private static void mergeInto(Map<String, Object> target, Map<String, Object> source) {
        source.forEach((key, value) -> {
            Object existing = target.get(key);
            if (existing instanceof Map<?, ?> existingMap && value instanceof Map<?, ?> valueMap) {
                mergeInto((Map<String, Object>) existingMap, (Map<String, Object>) valueMap);
                return;
            }
            target.put(key, value);
        });
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        return false;
    }

    private record ResolvedInbound(
            Map<String, Object> rawInput,
            Map<String, Object> autoInput,
            Map<String, Object> path,
            Map<String, Object> query,
            Map<String, Object> header,
            Map<String, Object> body,
            Map<String, Object> form,
            String rawBody
    ) {
    }
}
