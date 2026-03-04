package com.jimu.http.engine.step;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.jimu.http.config.JimuProperties;
import com.jimu.http.model.enums.StepType;
import com.jimu.http.support.LazyBeanMap;
import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(JimuProperties.class)
public class ScriptStepProcessor implements StepProcessor {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final JimuProperties jimuProperties;

    private final GroovyClassLoader scriptClassLoader = new GroovyClassLoader();
    private final Map<String, Class<? extends Script>> scriptClassCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Entry<String, Class<? extends Script>> eldest) {
                    return size() > jimuProperties.getScript().getCacheMax();
                }
            }
    );

    @Override
    public StepType getType() {
        return StepType.SCRIPT;
    }

    @Override
    public Object process(Object target, Map<String, Object> config, StepContext stepContext) {
        if (config == null) {
            return target;
        }
        String script = (String) config.get("script");
        if (StrUtil.isBlank(script)) {
            return target;
        }

        log.info("Executing custom script...");
        try {
            Binding binding = new Binding();
            binding.setVariable("body", target);
            binding.setVariable("context", stepContext.getContext());
            binding.setVariable("url", stepContext.getUrl());
            binding.setVariable("headers", stepContext.getHeaders());
            binding.setVariable("queryParams", stepContext.getQueryParams());
            binding.setVariable("log", log);
            if (redisTemplate != null) {
                binding.setVariable("redis", redisTemplate);
            }
            binding.setVariable("bean", createBeanAccessorClosure());
            binding.setVariable("beans", new LazyBeanMap(applicationContext));

            Class<? extends Script> scriptClass = getOrCompileScriptClass(script);
            Script groovyScript = InvokerHelper.createScript(scriptClass, binding);
            Object result = groovyScript.run();
            return result != null ? result : target;
        } catch (Exception e) {
            log.error("Execute script error", e);
            throw new RuntimeException("Script execution failed: " + e.getMessage());
        }
    }

    private Closure<Object> createBeanAccessorClosure() {
        return new Closure<Object>(this, this) {
            public Object doCall(String beanName) {
                if (StrUtil.isBlank(beanName)) {
                    throw new IllegalArgumentException("bean name is blank");
                }
                return applicationContext.getBean(beanName);
            }
        };
    }

    private Class<? extends Script> getOrCompileScriptClass(String script) {
        String key = SecureUtil.sha256(script);
        return scriptClassCache.computeIfAbsent(key, k -> scriptClassLoader.parseClass(script).asSubclass(Script.class));
    }
}
