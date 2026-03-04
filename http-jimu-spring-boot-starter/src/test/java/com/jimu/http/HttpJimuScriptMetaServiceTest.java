package com.jimu.http;

import com.jimu.http.config.JimuProperties;
import com.jimu.http.dto.script.BeanMetaDetail;
import com.jimu.http.dto.script.ScriptMeta;
import com.jimu.http.model.Result;
import com.jimu.http.service.HttpJimuScriptMetaService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpJimuScriptMetaServiceTest {

    private HttpJimuScriptMetaService service;

    @Test
    void shouldValidateScriptSuccessAndEmptyCase() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[0]);
        service = new HttpJimuScriptMetaService(ctx, new JimuProperties());

        Result<Map<String, Object>> empty = service.validateScript(Map.of("script", " "));
        assertEquals(1000, empty.getCode());
        assertFalse((Boolean) empty.getData().get("valid"));

        Result<Map<String, Object>> ok = service.validateScript(Map.of("script", "return body"));
        assertEquals(1000, ok.getCode());
        assertTrue((Boolean) ok.getData().get("valid"));
    }

    @Test
    void shouldBuildAndEvictScriptMetaCache() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"demoBean"});
        when(ctx.getType("demoBean")).thenAnswer(invocation -> String.class);
        service = new HttpJimuScriptMetaService(ctx, new JimuProperties());

        Result<ScriptMeta> first = service.scriptMeta();
        Result<ScriptMeta> second = service.scriptMeta();
        assertEquals(1000, first.getCode());
        assertEquals(1000, second.getCode());
        assertNotNull(first.getData());
        assertNotNull(second.getData());
        assertFalse(first.getData().getBeans().isEmpty());

        Result<Boolean> evict = service.evictScriptMetaCache();
        assertEquals(1000, evict.getCode());
        assertTrue(evict.getData());
    }

    @Test
    void shouldReturnBeanMetaAndErrorWhenMissing() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"demoBean"});
        when(ctx.containsBean("demoBean")).thenReturn(true);
        when(ctx.getBean("demoBean")).thenReturn("ok");
        service = new HttpJimuScriptMetaService(ctx, new JimuProperties());

        Result<BeanMetaDetail> ok = service.beanMeta("demoBean");
        assertEquals(1000, ok.getCode());
        assertNotNull(ok.getData());
        assertNotNull(ok.getData().getMethods());

        Result<BeanMetaDetail> missing = service.beanMeta("missingBean");
        assertEquals(1100, missing.getCode());
    }
}
