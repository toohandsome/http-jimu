package com.jimu.http;

import com.jimu.http.controller.HttpJimuController;
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
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class HttpJimuControllerTest {

    private HttpJimuService httpJimuService;
    private HttpJimuJobLogService jobLogService;
    private HttpJimuStepService stepService;
    private HttpJimuPoolService poolService;
    private HttpJimuScriptMetaService scriptMetaService;
    private HttpJimuEngine engine;
    private HttpJimuController controller;

    @BeforeEach
    void setUp() {
        httpJimuService = mock(HttpJimuService.class);
        jobLogService = mock(HttpJimuJobLogService.class);
        stepService = mock(HttpJimuStepService.class);
        poolService = mock(HttpJimuPoolService.class);
        scriptMetaService = mock(HttpJimuScriptMetaService.class);
        engine = mock(HttpJimuEngine.class);
        controller = new HttpJimuController(httpJimuService, jobLogService, stepService, poolService, engine, scriptMetaService);
    }

    @Test
    void shouldHandlePoolApis() {
        HttpJimuPool pool = new HttpJimuPool();
        pool.setId("p1");
        when(poolService.list()).thenReturn(List.of(pool));
        when(poolService.saveOrUpdate(any())).thenReturn(true);
        when(poolService.removeById("p1")).thenReturn(true);

        assertEquals(1, controller.listPools().getData().size());
        Result<Boolean> saved = controller.savePool(pool);
        assertEquals(1000, saved.getCode());
        verify(engine, times(1)).evictClientPool("p1");
        controller.deletePool("p1");
        verify(engine, times(2)).evictClientPool("p1");
    }

    @Test
    void shouldHandleStepApis() {
        HttpJimuStep step = new HttpJimuStep();
        step.setId("s1");
        when(stepService.list()).thenReturn(List.of(step));
        when(stepService.saveOrUpdate(any())).thenReturn(true);
        when(stepService.removeById("s1")).thenReturn(true);

        assertEquals(1, controller.listSteps().getData().size());
        assertTrue(controller.saveStep(step).getData());
        assertTrue(controller.deleteStep("s1").getData());
    }

    @Test
    void shouldReturnBusinessErrorWhenSavePoolFails() {
        HttpJimuPool pool = new HttpJimuPool();
        pool.setName("pool-1");
        when(poolService.saveOrUpdate(any())).thenThrow(new IllegalArgumentException("pool name already exists: pool-1"));

        Result<Boolean> result = controller.savePool(pool);

        assertEquals(1100, result.getCode());
        assertEquals("pool name already exists: pool-1", result.getMsg());
    }

    @Test
    void shouldReturnBusinessErrorWhenSaveStepFails() {
        HttpJimuStep step = new HttpJimuStep();
        step.setCode("STEP_1");
        when(stepService.saveOrUpdate(any())).thenThrow(new IllegalArgumentException("step code already exists: STEP_1"));

        Result<Boolean> result = controller.saveStep(step);

        assertEquals(1100, result.getCode());
        assertEquals("step code already exists: STEP_1", result.getMsg());
    }

    @Test
    void shouldValidateScriptSuccessAndFailure() {
        when(scriptMetaService.validateScript(Map.of("script", ""))).thenReturn(Result.success(Map.of("valid", false)));
        Result<Map<String, Object>> empty = controller.validateScript(Map.of("script", ""));
        assertFalse((Boolean) empty.getData().get("valid"));

        when(scriptMetaService.validateScript(Map.of("script", "return body"))).thenReturn(Result.success(Map.of("valid", true)));
        Result<Map<String, Object>> ok = controller.validateScript(Map.of("script", "return body"));
        assertTrue((Boolean) ok.getData().get("valid"));

        when(scriptMetaService.validateScript(Map.of("script", "if ("))).thenReturn(Result.success(Map.of("valid", false)));
        Result<Map<String, Object>> bad = controller.validateScript(Map.of("script", "if ("));
        assertFalse((Boolean) bad.getData().get("valid"));
    }

    @Test
    void shouldReturnConfigList() {
        HttpJimuConfig c = new HttpJimuConfig();
        c.setHttpId("h1");
        when(httpJimuService.list()).thenReturn(List.of(c));
        Result<List<HttpJimuConfig>> result = controller.list();
        assertEquals(1, result.getData().size());
    }

    @Test
    void shouldBuildAndCacheScriptMeta() {
        ScriptMeta mockMeta = new ScriptMeta();
        mockMeta.setVariables(List.of());
        when(scriptMetaService.scriptMeta()).thenReturn(Result.success(mockMeta));
        when(scriptMetaService.evictScriptMetaCache()).thenReturn(Result.success(true));
        ScriptMeta first = controller.scriptMeta().getData();
        ScriptMeta second = controller.scriptMeta().getData();
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(0, first.getVariables().size());
        assertTrue(controller.evictScriptMetaCache().getData());
    }

    @Test
    void shouldHandleBeanMetaApis() {
        BeanMetaDetail detail = new BeanMetaDetail();
        detail.setMethods(List.of());
        when(scriptMetaService.beanMeta("b1")).thenReturn(Result.success(detail));
        Result<BeanMetaDetail> ok = controller.beanMeta("b1");
        assertEquals(1000, ok.getCode());
        assertNotNull(ok.getData().getMethods());

        when(scriptMetaService.beanMeta(" ")).thenReturn(Result.error("bad"));
        Result<BeanMetaDetail> empty = controller.beanMeta(" ");
        assertEquals(1100, empty.getCode());

        when(scriptMetaService.beanMeta("missing")).thenReturn(Result.error("missing"));
        Result<BeanMetaDetail> miss = controller.beanMeta("missing");
        assertEquals(1100, miss.getCode());
    }

    @Test
    void shouldGetJobLogs() {
        HttpJimuJobLog log = HttpJimuJobLog.builder().httpId("h1").build();
        IPage<HttpJimuJobLog> page = new Page<>();
        page.setRecords(List.of(log));
        doReturn(page).when(jobLogService).page((IPage<HttpJimuJobLog>) any(), (Wrapper<HttpJimuJobLog>) any());
        Result<List<HttpJimuJobLog>> result = controller.getJobLogs("cfg1");
        assertEquals(1, result.getData().size());
    }

    @Test
    void shouldSaveWithPlaceholderValidationAndCronValidation() {
        HttpJimuConfig badCron = new HttpJimuConfig();
        badCron.setEnableJob(true);
        badCron.setCronConfig("bad");
        Result<Boolean> badCronResult = controller.save(badCron);
        assertEquals(1100, badCronResult.getCode());

        HttpJimuConfig badPlaceholder = new HttpJimuConfig();
        badPlaceholder.setEnableJob(false);
        badPlaceholder.setUrl("${x");
        Result<Boolean> badPlaceholderResult = controller.save(badPlaceholder);
        assertEquals(1100, badPlaceholderResult.getCode());

        HttpJimuConfig ok = new HttpJimuConfig();
        ok.setEnableJob(true);
        ok.setCronConfig(" 0/5 * * * * ? ");
        ok.setUrl("https://a.test");
        ok.setHeaders("[]");
        ok.setQueryParams("[]");
        ok.setBodyConfig("{}");
        ok.setStepsConfig("[]");
        ok.setParamsConfig("{}");
        when(httpJimuService.saveOrUpdate(any())).thenReturn(true);

        Result<Boolean> okResult = controller.save(ok);
        assertEquals(1000, okResult.getCode());
        assertEquals("0/5 * * * * ?", ok.getCronConfig());
    }

    @Test
    void shouldHandlePreviewAndTestCallSuccessAndFailure() {
        PreviewDetail preview = new PreviewDetail();
        preview.setRequestUrl("u");
        when(httpJimuService.preview(any(), any())).thenReturn(preview);
        Result<PreviewDetail> previewResult = controller.previewCall("h1", Map.of());
        assertEquals("u", previewResult.getData().getRequestUrl());

        doThrow(new RuntimeException("preview fail")).when(httpJimuService).preview(any(), any());
        Result<PreviewDetail> previewFail = controller.previewCall("h1", Map.of());
        assertEquals(1100, previewFail.getCode());

        ExecuteDetail detail = new ExecuteDetail();
        detail.setResponseBody("ok");
        when(httpJimuService.callWithDetail(any(), any())).thenReturn(detail);
        Result<ExecuteDetail> testResult = controller.testCall("h1", Map.of());
        assertEquals("ok", testResult.getData().getResponseBody());

        doThrow(new RuntimeException("test fail")).when(httpJimuService).callWithDetail(any(), any());
        Result<ExecuteDetail> testFail = controller.testCall("h1", Map.of());
        assertEquals(1100, testFail.getCode());
    }

    @Test
    void shouldDeleteConfig() {
        when(httpJimuService.removeById("id1")).thenReturn(true);
        Result<Boolean> result = controller.delete("id1");
        assertTrue(result.getData());
    }

    @Test
    void shouldValidatePlaceholderPatterns() {
        HttpJimuConfig config = new HttpJimuConfig();
        config.setEnableJob(false);
        config.setUrl("https://a.test/${env:APP_NAME}");
        config.setHeaders("[{\"key\":\"x\",\"value\":\"${redis:token}\"}]");
        config.setQueryParams("[{\"key\":\"q\",\"value\":\"${abc_1}\"}]");
        config.setBodyConfig("{}");
        config.setStepsConfig("[]");
        config.setParamsConfig("{}");
        when(httpJimuService.saveOrUpdate(any())).thenReturn(true);
        Result<Boolean> result = controller.save(config);
        assertEquals(1000, result.getCode());

        HttpJimuConfig bad = new HttpJimuConfig();
        bad.setEnableJob(false);
        bad.setUrl("${x y}");
        Result<Boolean> badResult = controller.save(bad);
        assertEquals(1100, badResult.getCode());
    }
}
