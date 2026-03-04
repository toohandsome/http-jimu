package com.jimu.http;

import com.jimu.http.engine.step.EncryptStepProcessor;
import com.jimu.http.engine.step.StepContext;
import com.jimu.http.engine.support.JimuExpressionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EncryptStepProcessorTest {

    @Test
    void shouldGenerateHmacAndWriteToTargetField() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(StringRedisTemplate.class));
        JimuExpressionResolver resolver = new JimuExpressionResolver(provider, mock(Environment.class));
        EncryptStepProcessor processor = new EncryptStepProcessor(resolver);

        Map<String, Object> target = new HashMap<>();
        target.put("orderId", "A100");
        Map<String, Object> config = new HashMap<>();
        config.put("algorithm", "HMAC_SHA256");
        config.put("secret", "test-secret");
        config.put("targetField", "sign");
        config.put("overwrite", false);
        config.put("fields", "[\"orderId\"]");

        StepContext context = StepContext.builder()
                .context(Map.of())
                .url("http://localhost")
                .headers(Map.of())
                .queryParams(Map.of())
                .build();

        Object out = processor.process(target, config, context);
        assertTrue(out instanceof Map);
        assertEquals("A100", ((Map<?, ?>) out).get("orderId"));
        assertNotNull(((Map<?, ?>) out).get("sign"));
    }

    @Test
    void shouldUseResolvedSecretPlaceholder() {
        Environment env = mock(Environment.class);
        when(env.getProperty("demo.secret")).thenReturn("env-secret");
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(StringRedisTemplate.class));
        JimuExpressionResolver resolver = new JimuExpressionResolver(provider, env);
        EncryptStepProcessor processor = new EncryptStepProcessor(resolver);

        Map<String, Object> target = new HashMap<>();
        target.put("payload", "hello");
        Map<String, Object> config = new HashMap<>();
        config.put("algorithm", "HMAC_SHA1");
        config.put("secret", "${env:demo.secret}");
        config.put("fields", "[\"payload\"]");
        config.put("overwrite", true);

        StepContext context = StepContext.builder()
                .context(Map.of())
                .url("http://localhost")
                .headers(Map.of())
                .queryParams(Map.of())
                .build();

        Object out = processor.process(target, config, context);
        assertTrue(out instanceof Map);
        Object val = ((Map<?, ?>) out).get("payload");
        assertNotNull(val);
        assertTrue(String.valueOf(val).length() > 10);
    }

    @Test
    void shouldThrowWhenRequiredSecretMissing() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(StringRedisTemplate.class));
        JimuExpressionResolver resolver = new JimuExpressionResolver(provider, mock(Environment.class));
        EncryptStepProcessor processor = new EncryptStepProcessor(resolver);

        Map<String, Object> target = new HashMap<>();
        target.put("orderId", "A100");
        Map<String, Object> config = new HashMap<>();
        config.put("algorithm", "HMAC_SHA256");
        config.put("fields", "[\"orderId\"]");

        StepContext context = StepContext.builder()
                .context(Map.of())
                .url("http://localhost")
                .headers(Map.of())
                .queryParams(Map.of())
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> processor.process(target, config, context));
        assertTrue(ex.getMessage().contains("ENCRYPT step failed"));
    }
}
