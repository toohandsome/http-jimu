package com.jimu.http;

import com.jimu.http.config.JimuProperties;
import com.jimu.http.engine.HttpJimuEngine;
import com.jimu.http.engine.HttpJimuScheduler;
import com.jimu.http.engine.model.ExecuteDetail;
import com.jimu.http.engine.model.PreviewDetail;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.mapper.HttpJimuConfigMapper;
import com.jimu.http.service.HttpJimuService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpJimuServiceTest {

    @Test
    void shouldScheduleEnabledConfigsOnInit() {
        HttpJimuEngine engine = mock(HttpJimuEngine.class);
        HttpJimuScheduler scheduler = mock(HttpJimuScheduler.class);
        HttpJimuService service = spy(new HttpJimuService(engine, scheduler, new JimuProperties()));

        HttpJimuConfig c1 = new HttpJimuConfig();
        c1.setId("1");
        c1.setEnableJob(true);
        c1.setCronConfig("0/5 * * * * ?");
        HttpJimuConfig c2 = new HttpJimuConfig();
        c2.setId("2");
        c2.setEnableJob(true);
        c2.setCronConfig("bad");
        doReturn(List.of(c1, c2)).when(service).list((Wrapper<HttpJimuConfig>) any());

        service.initScheduledTasks();

        verify(scheduler).schedule(c1);
    }

    @Test
    void shouldRejectInvalidCronBeforeSaveOrUpdate() {
        HttpJimuService service = spy(new HttpJimuService(mock(HttpJimuEngine.class), mock(HttpJimuScheduler.class), new JimuProperties()));
        HttpJimuConfig c = new HttpJimuConfig();
        c.setEnableJob(true);
        c.setCronConfig("invalid");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.saveOrUpdate(c));
        assertTrue(ex.getMessage().contains("Cron"));
    }

    @Test
    void shouldCallEngineWithDetail() {
        HttpJimuEngine engine = mock(HttpJimuEngine.class);
        HttpJimuService service = spy(new HttpJimuService(engine, mock(HttpJimuScheduler.class), new JimuProperties()));
        HttpJimuConfig c = new HttpJimuConfig();
        c.setHttpId("h1");
        doReturn(c).when(service).getByHttpId("h1");
        ExecuteDetail detail = new ExecuteDetail();
        detail.setResponseBody("ok");
        when(engine.executeWithDetail(eq(c), any())).thenReturn(detail);

        ExecuteDetail got = service.callWithDetail("h1", Map.of("a", 1));
        assertEquals("ok", got.getResponseBody());
    }

    @Test
    void shouldThrowWhenConfigNotFound() {
        HttpJimuService service = spy(new HttpJimuService(mock(HttpJimuEngine.class), mock(HttpJimuScheduler.class), new JimuProperties()));
        doReturn(null).when(service).getByHttpId("missing");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.callWithDetail("missing", Map.of()));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void shouldPreviewThroughEngine() {
        HttpJimuEngine engine = mock(HttpJimuEngine.class);
        HttpJimuService service = spy(new HttpJimuService(engine, mock(HttpJimuScheduler.class), new JimuProperties()));
        HttpJimuConfig c = new HttpJimuConfig();
        c.setHttpId("h2");
        doReturn(c).when(service).getByHttpId("h2");
        PreviewDetail detail = new PreviewDetail();
        detail.setRequestUrl("u");
        when(engine.previewWithSteps(eq(c), any())).thenReturn(detail);

        PreviewDetail got = service.preview("h2", Map.of());
        assertEquals("u", got.getRequestUrl());
    }

    @Test
    void shouldCallReturnBody() {
        HttpJimuService service = spy(new HttpJimuService(mock(HttpJimuEngine.class), mock(HttpJimuScheduler.class), new JimuProperties()));
        ExecuteDetail detail = new ExecuteDetail();
        detail.setResponseBody("body");
        doReturn(detail).when(service).callWithDetail(eq("h3"), any());
        assertEquals("body", service.call("h3", Map.of()));
    }

    @Test
    void shouldUseCacheInGetByHttpId() {
        HttpJimuService service = spy(new HttpJimuService(mock(HttpJimuEngine.class), mock(HttpJimuScheduler.class), new JimuProperties()));
        HttpJimuConfig c = new HttpJimuConfig();
        c.setHttpId("cache-id");
        doReturn(c).when(service).getOne(any());

        HttpJimuConfig first = service.getByHttpId("cache-id");
        HttpJimuConfig second = service.getByHttpId("cache-id");

        assertNotNull(first);
        assertEquals(first, second);
        verify(service).getOne(any());
    }

    @Test
    void shouldReturnNullWhenHttpIdBlank() {
        HttpJimuService service = new HttpJimuService(mock(HttpJimuEngine.class), mock(HttpJimuScheduler.class), new JimuProperties());
        assertNull(service.getByHttpId(" "));
    }

    @Test
    void shouldEvictAndClearCache() {
        HttpJimuService service = spy(new HttpJimuService(mock(HttpJimuEngine.class), mock(HttpJimuScheduler.class), new JimuProperties()));
        HttpJimuConfig c = new HttpJimuConfig();
        c.setHttpId("h4");
        doReturn(c).when(service).getOne(any());
        service.getByHttpId("h4");
        assertNotNull(service.getByHttpId("h4"));

        service.evictHttpIdCache("h4");
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HttpJimuConfig>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(service).getOne(captor.capture());

        service.clearHttpIdCache();
        service.evictHttpIdCache(" ");
    }

    @Test
    void shouldNotScheduleWhenSaveOrUpdateReturnsFalse() {
        HttpJimuEngine engine = mock(HttpJimuEngine.class);
        HttpJimuScheduler scheduler = mock(HttpJimuScheduler.class);
        HttpJimuService service = spy(new HttpJimuService(engine, scheduler, new JimuProperties()));
        HttpJimuConfigMapper mapper = mock(HttpJimuConfigMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        when(mapper.insert(any(HttpJimuConfig.class))).thenReturn(0);
        when(mapper.updateById(any(HttpJimuConfig.class))).thenReturn(0);

        HttpJimuConfig entity = new HttpJimuConfig();
        entity.setHttpId("new-http");
        entity.setEnableJob(false);
        entity.setMethod("POST");

        boolean ok = service.saveOrUpdate(entity);
        assertTrue(!ok);
        verify(scheduler, never()).schedule(any(HttpJimuConfig.class));
    }

    @Test
    void shouldRemoveByIdAndCancelScheduleWhenMapperSuccess() {
        HttpJimuEngine engine = mock(HttpJimuEngine.class);
        HttpJimuScheduler scheduler = mock(HttpJimuScheduler.class);
        HttpJimuService service = spy(new HttpJimuService(engine, scheduler, new JimuProperties()));
        HttpJimuConfigMapper mapper = mock(HttpJimuConfigMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        HttpJimuConfig old = new HttpJimuConfig();
        old.setId("1");
        old.setHttpId("old-http");
        when(mapper.selectById(any())).thenReturn(old);
        when(mapper.deleteById("1")).thenReturn(1);

        boolean ok = service.removeById("1");
        assertTrue(ok);
        verify(scheduler).cancel("1");
    }
}
