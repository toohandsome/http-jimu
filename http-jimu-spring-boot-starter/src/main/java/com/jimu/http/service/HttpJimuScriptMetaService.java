package com.jimu.http.service;

import com.jimu.http.cache.JimuCacheProvider;
import com.jimu.http.cache.MemoryJimuCacheProvider;
import com.jimu.http.config.JimuProperties;
import com.jimu.http.dto.script.BeanMetaDetail;
import com.jimu.http.dto.script.MetaBean;
import com.jimu.http.dto.script.MetaClass;
import com.jimu.http.dto.script.MetaFunction;
import com.jimu.http.dto.script.MetaMethod;
import com.jimu.http.dto.script.MetaVariable;
import com.jimu.http.dto.script.ScriptMeta;
import com.jimu.http.model.Result;
import groovy.lang.GroovyShell;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(JimuProperties.class)
public class HttpJimuScriptMetaService {

    private static final Pattern GROOVY_LINE_COL = Pattern.compile("line\\s+(\\d+),\\s*column\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final String CACHE_NAME_SCRIPT_META = "script_meta";
    private static final String CACHE_NAME_BEAN_META = "bean_meta";
    private static final String CACHE_KEY_SCRIPT_META = "default";

    private final ApplicationContext applicationContext;
    private final JimuProperties jimuProperties;
    private final JimuCacheProvider fallbackCacheProvider = new MemoryJimuCacheProvider();

    @Autowired(required = false)
    private JimuCacheProvider cacheProvider;

    public Result<Map<String, Object>> validateScript(Map<String, String> payload) {
        String script = payload != null ? payload.get("script") : null;
        Map<String, Object> result = new HashMap<>();
        if (script == null || script.trim().isEmpty()) {
            result.put("valid", false);
            result.put("message", "Script cannot be empty");
            result.put("line", 1);
            result.put("column", 1);
            return Result.success(result);
        }

        try {
            // Syntax/compile validation only: parse does not execute script.
            new GroovyShell().parse(script);
            result.put("valid", true);
            result.put("message", "Groovy syntax/compile validation succeeded");
            result.put("line", 1);
            result.put("column", 1);
            return Result.success(result);
        } catch (Exception e) {
            result.put("valid", false);
            result.put("message", e.getMessage() != null ? e.getMessage() : "Groovy syntax validation failed");
            fillLineAndColumn(result, e);
            return Result.success(result);
        }
    }

    public Result<ScriptMeta> scriptMeta() {
        ScriptMeta snapshot = cache().get(CACHE_NAME_SCRIPT_META, CACHE_KEY_SCRIPT_META, ScriptMeta.class);
        if (snapshot != null) {
            return Result.success(snapshot);
        }

        ScriptMeta meta = new ScriptMeta();
        meta.setVariables(buildScriptVariables());
        meta.setFunctions(buildScriptFunctions());
        meta.setMembers(buildScriptMembers());
        meta.setClasses(buildScriptClasses());
        meta.setBeans(buildBeansMeta());
        cache().put(CACHE_NAME_SCRIPT_META, CACHE_KEY_SCRIPT_META, meta, jimuProperties.getCache().getScriptMetaTtlMs());
        return Result.success(meta);
    }

    public Result<BeanMetaDetail> beanMeta(String beanName) {
        if (beanName == null || beanName.isBlank()) {
            return Result.error("Bean name cannot be blank");
        }
        if (!applicationContext.containsBean(beanName)) {
            return Result.error("Bean not found: " + beanName);
        }

        BeanMetaDetail cached = cache().get(CACHE_NAME_BEAN_META, beanName, BeanMetaDetail.class);
        if (cached != null) {
            return Result.success(cached);
        }

        Object bean = applicationContext.getBean(beanName);
        Class<?> beanType = bean.getClass();
        BeanMetaDetail detail = new BeanMetaDetail();
        detail.setBeanName(beanName);
        detail.setType(beanType.getName());
        detail.setMethods(buildTypeMethods(beanType, 300));
        cache().put(CACHE_NAME_BEAN_META, beanName, detail, jimuProperties.getCache().getBeanMetaTtlMs());
        return Result.success(detail);
    }

    public Result<Boolean> evictScriptMetaCache() {
        cache().clear(CACHE_NAME_SCRIPT_META);
        cache().clear(CACHE_NAME_BEAN_META);
        return Result.success(true);
    }

    private void fillLineAndColumn(Map<String, Object> result, Throwable e) {
        int line = 1;
        int column = 1;
        String msg = e.getMessage() != null ? e.getMessage() : "";
        Matcher matcher = GROOVY_LINE_COL.matcher(msg);
        if (matcher.find()) {
            try {
                line = Integer.parseInt(matcher.group(1));
                column = Integer.parseInt(matcher.group(2));
            } catch (Exception ignore) {
                // ignore parse error
            }
        } else if (e.getStackTrace() != null) {
            for (StackTraceElement ste : e.getStackTrace()) {
                if (ste.getFileName() != null && ste.getFileName().startsWith("Script")) {
                    line = ste.getLineNumber() > 0 ? ste.getLineNumber() : 1;
                    break;
                }
            }
        }
        result.put("line", line);
        result.put("column", column);
    }

    private List<MetaVariable> buildScriptVariables() {
        List<MetaVariable> list = new ArrayList<>();
        list.add(new MetaVariable("body", "java.lang.Object", "Current payload object"));
        list.add(new MetaVariable("context", "java.util.Map<String,Object>", "Input context map"));
        list.add(new MetaVariable("url", "java.lang.String", "Request URL"));
        list.add(new MetaVariable("headers", "java.util.Map<String,String>", "Request headers"));
        list.add(new MetaVariable("queryParams", "java.util.Map<String,String>", "Query parameters"));
        list.add(new MetaVariable("log", "org.slf4j.Logger", "Logger"));
        list.add(new MetaVariable("redis", "org.springframework.data.redis.core.StringRedisTemplate", "Redis template if available"));
        list.add(new MetaVariable("bean", "groovy.lang.Closure", "Get Spring bean by name"));
        list.add(new MetaVariable("beans", "java.util.Map<String,Object>", "Lazy bean map by name"));
        return list;
    }

    private List<MetaFunction> buildScriptFunctions() {
        List<MetaFunction> list = new ArrayList<>();
        list.add(new MetaFunction("return body;", "return body;", "Return transformed payload", Arrays.asList("body"), "java.lang.Object"));
        list.add(new MetaFunction("body.put(key, value)", "body.put(${1:key}, ${2:value})", "Put field into map payload", Arrays.asList("key", "value"), "java.lang.Object"));
        list.add(new MetaFunction("context.get(key)", "context.get(${1:key})", "Read value from context", Arrays.asList("key"), "java.lang.Object"));
        list.add(new MetaFunction("log.info(msg)", "log.info(${1:msg})", "Write log", Arrays.asList("msg"), "void"));
        list.add(new MetaFunction("bean(name)", "bean(\"${1:orderService}\")", "Get Spring bean by name", Arrays.asList("name"), "java.lang.Object"));
        list.add(new MetaFunction("bean(name).method(...)", "bean(\"${1:orderService}\").${2:createOrder}(${3})", "Invoke bean method", Arrays.asList("name", "method"), "java.lang.Object"));
        list.add(new MetaFunction("JSON.parseObject(text)", "JSON.parseObject(${1:text})", "Parse object by fastjson2", Arrays.asList("text"), "com.alibaba.fastjson2.JSONObject"));
        list.add(new MetaFunction("JSON.parseArray(text)", "JSON.parseArray(${1:text})", "Parse array by fastjson2", Arrays.asList("text"), "com.alibaba.fastjson2.JSONArray"));
        list.add(new MetaFunction("JSON.toJSONString(obj)", "JSON.toJSONString(${1:obj})", "Serialize by fastjson2", Arrays.asList("obj"), "java.lang.String"));
        list.add(new MetaFunction("JSONObject.of(...)", "JSONObject.of(${1:key}, ${2:value})", "Build JSONObject", Arrays.asList("key", "value"), "com.alibaba.fastjson2.JSONObject"));
        list.add(new MetaFunction("JSONArray.of(...)", "JSONArray.of(${1:value})", "Build JSONArray", Arrays.asList("value"), "com.alibaba.fastjson2.JSONArray"));
        return list;
    }

    private Map<String, List<MetaMethod>> buildScriptMembers() {
        Map<String, List<MetaMethod>> members = new HashMap<>();
        members.put("body", buildTypeMethods(Map.class, 120));
        members.put("context", buildTypeMethods(Map.class, 120));
        members.put("headers", buildTypeMethods(Map.class, 120));
        members.put("queryParams", buildTypeMethods(Map.class, 120));
        members.put("url", buildTypeMethods(String.class, 120));
        members.put("log", buildTypeMethods(org.slf4j.Logger.class, 120));
        members.put("JSON", buildTypeMethods(com.alibaba.fastjson2.JSON.class, 200));
        members.put("JSONObject", buildTypeMethods(com.alibaba.fastjson2.JSONObject.class, 200));
        members.put("JSONArray", buildTypeMethods(com.alibaba.fastjson2.JSONArray.class, 200));
        try {
            Class<?> redisClass = Class.forName("org.springframework.data.redis.core.StringRedisTemplate");
            members.put("redis", buildTypeMethods(redisClass, 200));
        } catch (Throwable ignore) {
            members.put("redis", new ArrayList<>());
        }
        return members;
    }

    private List<MetaClass> buildScriptClasses() {
        List<MetaClass> classes = new ArrayList<>();
        classes.add(new MetaClass("java.util.Map", "Map interface"));
        classes.add(new MetaClass("java.util.HashMap", "HashMap implementation"));
        classes.add(new MetaClass("java.util.List", "List interface"));
        classes.add(new MetaClass("java.util.ArrayList", "ArrayList implementation"));
        classes.add(new MetaClass("java.math.BigDecimal", "High precision number"));
        classes.add(new MetaClass("java.time.LocalDateTime", "DateTime"));
        classes.add(new MetaClass("java.time.LocalDate", "Date"));
        classes.add(new MetaClass("java.lang.String", "String"));
        classes.add(new MetaClass("com.alibaba.fastjson2.JSON", "fastjson2 JSON utility"));
        classes.add(new MetaClass("com.alibaba.fastjson2.JSONObject", "fastjson2 JSONObject"));
        classes.add(new MetaClass("com.alibaba.fastjson2.JSONArray", "fastjson2 JSONArray"));
        return classes;
    }

    private List<MetaBean> buildBeansMeta() {
        List<MetaBean> beans = new ArrayList<>();
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        if (beanNames == null) {
            return beans;
        }

        Arrays.sort(beanNames);
        for (String beanName : beanNames) {
            if (beanName == null || beanName.isBlank()) {
                continue;
            }
            Class<?> type = applicationContext.getType(beanName);
            beans.add(new MetaBean(beanName, type != null ? type.getName() : "unknown"));
        }
        return beans;
    }

    private List<MetaMethod> buildTypeMethods(Class<?> type, int maxSize) {
        List<MetaMethod> methods = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (Method method : type.getMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            String signatureKey = method.getName() + "#" + method.getParameterCount();
            if (!unique.add(signatureKey)) {
                continue;
            }

            List<String> params = new ArrayList<>();
            StringBuilder insert = new StringBuilder(method.getName()).append("(");
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                String p = "arg" + (i + 1);
                params.add(shortType(parameterTypes[i]) + " " + p);
                insert.append("${").append(i + 1).append(":").append(p).append("}");
                if (i < parameterTypes.length - 1) {
                    insert.append(", ");
                }
            }
            insert.append(")");

            String detail = shortType(method.getReturnType()) + " " + method.getName();
            methods.add(new MetaMethod(method.getName(), insert.toString(), detail, params, method.getReturnType().getName()));
            if (methods.size() >= maxSize) {
                break;
            }
        }
        methods.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return methods;
    }

    private String shortType(Class<?> type) {
        if (type == null) {
            return "Object";
        }
        if (type.isArray()) {
            return shortType(type.getComponentType()) + "[]";
        }
        return type.getSimpleName();
    }

    private JimuCacheProvider cache() {
        return cacheProvider != null ? cacheProvider : fallbackCacheProvider;
    }
}
