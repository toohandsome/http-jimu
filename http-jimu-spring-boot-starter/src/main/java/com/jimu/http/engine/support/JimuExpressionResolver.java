package com.jimu.http.engine.support;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class JimuExpressionResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Environment environment;

    /**
     * 解析占位符：
     * ${redis:key} -> Redis 值
     * ${env:key}   -> Spring 环境变量值
     * ${key}       -> 输入参数 context 中的值
     */
    public String resolve(String template, Map<String, Object> context) {
        if (StrUtil.isBlank(template)) return template;

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            sb.append(template, lastEnd, matcher.start());
            String key = matcher.group(1);
            String value = "";

            if (key.startsWith("redis:")) {
                StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
                if (redisTemplate != null) {
                    value = redisTemplate.opsForValue().get(key.substring(6));
                } else {
                    log.warn("Redis is not configured, cannot resolve: {}", key);
                }
            } else if (key.startsWith("env:")) {
                value = environment.getProperty(key.substring(4));
            } else {
                Object val = context != null ? context.get(key) : null;
                value = val != null ? String.valueOf(val) : "";
            }

            sb.append(value != null ? value : "");
            lastEnd = matcher.end();
        }
        sb.append(template.substring(lastEnd));
        return sb.toString();
    }
}
