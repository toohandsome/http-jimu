package com.jimu.http;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jimu.http.config.JimuProperties;
import com.jimu.http.engine.HttpJimuEngine;
import com.jimu.http.engine.model.ExecuteDetail;
import com.jimu.http.engine.model.PreviewDetail;
import com.jimu.http.engine.step.AddFixedStepProcessor;
import com.jimu.http.engine.step.ScriptStepProcessor;
import com.jimu.http.engine.step.SignStepProcessor;
import com.jimu.http.engine.step.SortStepProcessor;
import com.jimu.http.engine.step.StepContext;
import com.jimu.http.engine.support.HttpJimuTransportSupport;
import com.jimu.http.engine.support.JimuExpressionResolver;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.entity.HttpJimuPool;
import com.jimu.http.entity.HttpJimuStep;
import com.jimu.http.service.HttpJimuPoolService;
import com.jimu.http.service.HttpJimuStepService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpJimuEngineCoreTest {

    private HttpJimuEngine engine;
    private Environment environment;
    private StringRedisTemplate redisTemplate;
    private ObjectProvider<StringRedisTemplate> redisProvider;
    private JimuExpressionResolver expressionResolver;
    private HttpJimuStepService stepService;
    private ApplicationContext applicationContext;
    private HttpJimuTransportSupport transportSupport;
    private HttpJimuPoolService poolService;
    private SignStepProcessor signProcessor;
    private ScriptStepProcessor scriptProcessor;
    private JimuProperties jimuProperties;

    @BeforeEach
    void setUp() {
        jimuProperties = new JimuProperties();

        environment = mock(Environment.class);
        redisTemplate = mock(StringRedisTemplate.class);
        redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        expressionResolver = new JimuExpressionResolver(redisProvider, environment);
        stepService = mock(HttpJimuStepService.class);
        applicationContext = mock(ApplicationContext.class);
        transportSupport = mock(HttpJimuTransportSupport.class);
        poolService = mock(HttpJimuPoolService.class);

        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[0]);
        when(transportSupport.mergeUrlQueryParams(anyString(), any(Map.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        engine = new HttpJimuEngine(jimuProperties);

        ReflectionTestUtils.setField(engine, "expressionResolver", expressionResolver);
        ReflectionTestUtils.setField(engine, "stepService", stepService);
        ReflectionTestUtils.setField(engine, "transportSupport", transportSupport);
        ReflectionTestUtils.setField(engine, "poolService", poolService);

        signProcessor = new SignStepProcessor(expressionResolver);
        scriptProcessor = new ScriptStepProcessor(jimuProperties);
        ReflectionTestUtils.setField(scriptProcessor, "applicationContext", applicationContext);
        ReflectionTestUtils.setField(scriptProcessor, "redisTemplate", redisTemplate);

        engine.setStepProcessors(List.of(
                new SortStepProcessor(),
                signProcessor,
                new AddFixedStepProcessor(),
                scriptProcessor
        ));
    }

    @Test
    void shouldResolveValueFromContextEnvAndRedis() {
        when(environment.getProperty("app.name")).thenReturn("demo-app");
        StringRedisTemplate localRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(localRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("token")).thenReturn("redis-token");

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("orderId", "A100");
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(localRedisTemplate);

        JimuExpressionResolver resolver = new JimuExpressionResolver(provider, environment);
        String resolved = resolver.resolve("id=${orderId},env=${env:app.name},redis=${redis:token}", ctx);

        assertEquals("id=A100,env=demo-app,redis=redis-token", resolved);
    }

    @Test
    void shouldParseKvConfigFromArrayAndObjectFallback() throws Exception {
        Map<String, Object> ctx = Map.of("k", "v1");
        @SuppressWarnings("unchecked")
        Map<String, String> fromArray = (Map<String, String>) invokePrivate(
                "parseKvConfig",
                new Class[]{String.class, Map.class},
                "[{\"key\":\"a\",\"value\":\"${k}\"}]",
                ctx
        );
        assertEquals("v1", fromArray.get("a"));

        @SuppressWarnings("unchecked")
        Map<String, String> fromObject = (Map<String, String>) invokePrivate(
                "parseKvConfig",
                new Class[]{String.class, Map.class},
                "{\"b\":\"${k}\"}",
                ctx
        );
        assertEquals("v1", fromObject.get("b"));

        @SuppressWarnings("unchecked")
        Map<String, String> invalid = (Map<String, String>) invokePrivate(
                "parseKvConfig",
                new Class[]{String.class, Map.class},
                "not-json",
                ctx
        );
        assertTrue(invalid.isEmpty());
    }

    @Test
    void shouldInitializeBodyByBodyType() throws Exception {
        HttpJimuConfig config = new HttpJimuConfig();
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("a", "1");

        config.setBodyType("none");
        assertNull(invokePrivate("initializeBody", new Class[]{HttpJimuConfig.class, Map.class}, config, ctx));

        config.setBodyType("raw");
        config.setBodyConfig("{\"x\":\"y\"}");
        Object raw = invokePrivate("initializeBody", new Class[]{HttpJimuConfig.class, Map.class}, config, ctx);
        assertTrue(raw instanceof Map);
        assertEquals("y", ((Map<?, ?>) raw).get("x"));

        config.setBodyType("form-data");
        config.setBodyConfig("[{\"key\":\"f1\",\"value\":\"${a}\"}]");
        Object form = invokePrivate("initializeBody", new Class[]{HttpJimuConfig.class, Map.class}, config, ctx);
        assertTrue(form instanceof Map);
        assertEquals("1", ((Map<?, ?>) form).get("f1"));

        config.setBodyType("raw");
        config.setBodyConfig("");
        Object defaultBody = invokePrivate("initializeBody", new Class[]{HttpJimuConfig.class, Map.class}, config, ctx);
        assertEquals("1", ((Map<?, ?>) defaultBody).get("a"));
    }

    @Test
    void shouldResolveLibraryStepAndMergeConfig() throws Exception {
        HttpJimuStep libraryStep = new HttpJimuStep();
        libraryStep.setCode("LIB1");
        libraryStep.setType("SCRIPT");
        libraryStep.setTarget("BODY");
        libraryStep.setConfigJson("{\"k\":\"v\"}");
        libraryStep.setScriptContent("return body");
        when(stepService.list((Wrapper<HttpJimuStep>) any())).thenReturn(List.of(libraryStep));

        @SuppressWarnings("unchecked")
        List<Object> steps = (List<Object>) invokePrivate(
                "resolveSteps",
                new Class[]{String.class},
                "[{\"stepCode\":\"LIB1\",\"config\":{\"x\":\"y\"}}]"
        );

        assertEquals(1, steps.size());
        Object step = steps.get(0);
        Method getType = step.getClass().getMethod("getType");
        Method getTarget = step.getClass().getMethod("getTarget");
        Method getConfig = step.getClass().getMethod("getConfig");
        assertEquals("SCRIPT", getType.invoke(step));
        assertEquals("BODY", getTarget.invoke(step));

        @SuppressWarnings("unchecked")
        Map<String, Object> cfg = (Map<String, Object>) getConfig.invoke(step);
        assertEquals("v", cfg.get("k"));
        assertEquals("y", cfg.get("x"));
        assertEquals("return body", cfg.get("script"));
    }

    @Test
    void shouldSignAndEncryptParams() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("b", "2");
        body.put("a", "1");
        Map<String, Object> signCfg = new HashMap<>();
        signCfg.put("algorithm", "MD5");
        signCfg.put("targetField", "sign");
        signCfg.put("salt", "S");

        StepContext ctx = StepContext.builder()
                .context(Map.of())
                .url("u")
                .headers(new HashMap<>())
                .queryParams(new HashMap<>())
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> signed = (Map<String, Object>) signProcessor.process(body, signCfg, ctx);
        assertEquals(SecureUtil.md5("a=1&b=2S"), signed.get("sign"));
    }

    @Test
    void shouldPreviewWithRequestScriptStep() {
        HttpJimuConfig config = new HttpJimuConfig();
        config.setHttpId("h1");
        config.setMethod("POST");
        config.setUrl("https://x.test/api");
        config.setHeaders("[]");
        config.setQueryParams("[]");
        config.setBodyType("raw");
        config.setBodyConfig("{\"a\":\"b\"}");
        config.setStepsConfig("[{\"type\":\"SCRIPT\",\"target\":\"BODY\",\"config\":{\"script\":\"body.put('x','1'); return body\"},\"enableLog\":false}]");

        PreviewDetail detail = engine.previewWithSteps(config, Map.of());
        assertEquals("POST", detail.getRequestMethod());
        assertTrue(detail.getRequestBody().contains("\"x\":\"1\""));
        assertFalse(detail.getStepTraces().isEmpty());
    }

    @Test
    void shouldApplyResponseStatusStepOnExecute() {
        ExecuteDetail response = new ExecuteDetail();
        response.setRequestMethod("POST");
        response.setRequestUrl("https://x.test/api");
        response.setRequestHeaders(new LinkedHashMap<>());
        response.setResponseStatus(200);
        response.setResponseHeaders(new LinkedHashMap<>());
        response.setResponseBody("{\"ok\":true}");
        when(transportSupport.sendRequestWithDetail(any(), anyString(), anyString(), any(Map.class), any()))
                .thenReturn(response);

        HttpJimuConfig config = new HttpJimuConfig();
        config.setHttpId("h2");
        config.setMethod("POST");
        config.setUrl("https://x.test/api");
        config.setHeaders("[]");
        config.setQueryParams("[]");
        config.setBodyType("raw");
        config.setBodyConfig("{\"a\":\"b\"}");
        config.setStepsConfig("[{\"type\":\"SCRIPT\",\"target\":\"RESPONSE_STATUS\",\"config\":{\"script\":\"body.status=201; return body\"},\"enableLog\":false}]");

        ExecuteDetail detail = engine.executeWithDetail(config, Map.of());
        assertEquals(201, detail.getResponseStatus());
        verify(transportSupport).sendRequestWithDetail(any(), anyString(), anyString(), any(Map.class), any());
    }

    @Test
    void shouldInheritRetryConfigFromPoolWhenConfigNotSet() {
        ExecuteDetail retryResponse = new ExecuteDetail();
        retryResponse.setResponseStatus(503);
        retryResponse.setRequestHeaders(new LinkedHashMap<>());
        retryResponse.setResponseHeaders(new LinkedHashMap<>());
        retryResponse.setResponseBody("busy");

        ExecuteDetail successResponse = new ExecuteDetail();
        successResponse.setResponseStatus(200);
        successResponse.setRequestHeaders(new LinkedHashMap<>());
        successResponse.setResponseHeaders(new LinkedHashMap<>());
        successResponse.setResponseBody("ok");

        when(transportSupport.sendRequestWithDetail(any(), anyString(), anyString(), any(Map.class), any()))
                .thenReturn(retryResponse, successResponse);

        HttpJimuPool pool = new HttpJimuPool();
        pool.setId("pool-1");
        pool.setRetryMaxAttempts(1);
        pool.setRetryOnHttpStatus("503");
        when(poolService.getById("pool-1")).thenReturn(pool);

        HttpJimuConfig config = new HttpJimuConfig();
        config.setHttpId("h3");
        config.setPoolId("pool-1");
        config.setMethod("GET");
        config.setUrl("https://x.test/retry");
        config.setHeaders("[]");
        config.setQueryParams("[]");
        config.setBodyType("none");

        ExecuteDetail detail = engine.executeWithDetail(config, Map.of());
        assertEquals(200, detail.getResponseStatus());
        verify(poolService, times(2)).getById("pool-1");
        verify(transportSupport, times(2)).sendRequestWithDetail(any(), anyString(), anyString(), any(Map.class), any());
    }

    @Test
    void shouldDelegateEvictClientPool() {
        engine.evictClientPool("pool-1");
        verify(transportSupport).evictClientPool("pool-1");
    }

    @Test
    void shouldHandleExtractFieldsAndUnsupportedAlgorithm() {
        Map<String, Object> encCfg = new HashMap<>();
        encCfg.put("algorithm", "UNKNOWN");
        encCfg.put("targetField", "sign");
        assertThrows(IllegalArgumentException.class, () -> signProcessor.process(
                new HashMap<>(Map.of("a", "1")),
                encCfg,
                StepContext.builder().context(Map.of()).url("u").headers(new HashMap<>()).queryParams(new HashMap<>()).build()
        ));
    }

    @Test
    void shouldRunScriptUsingBeanClosure() {
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{"demoBean"});
        when(applicationContext.getBean("demoBean")).thenReturn("bean-v");

        StepContext ctx = StepContext.builder()
                .context(new HashMap<>())
                .url("u")
                .headers(new HashMap<>())
                .queryParams(new HashMap<>())
                .build();

        Object result = scriptProcessor.process(
                new HashMap<>(Map.of("x", "1")),
                new HashMap<>(Map.of("script", "def b=bean('demoBean'); body.put('bean', b); return body")),
                ctx
        );

        assertTrue(result instanceof Map);
        assertEquals("bean-v", ((Map<?, ?>) result).get("bean"));
    }

    @Test
    void shouldResolveStepsWhenNull() throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> steps = (List<Object>) invokePrivate("resolveSteps", new Class[]{String.class}, (Object) null);
        assertNotNull(steps);
        assertTrue(steps.isEmpty());
    }

    private Object invokePrivate(String methodName, Class<?>[] argTypes, Object... args) throws Exception {
        Method method = HttpJimuEngine.class.getDeclaredMethod(methodName, argTypes);
        method.setAccessible(true);
        return method.invoke(engine, args);
    }
}
