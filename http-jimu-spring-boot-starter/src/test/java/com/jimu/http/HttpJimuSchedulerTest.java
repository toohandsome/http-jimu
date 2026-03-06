package com.jimu.http;

import com.jimu.http.config.JimuProperties;
import com.jimu.http.engine.HttpJimuEngine;
import com.jimu.http.engine.HttpJimuScheduler;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.entity.HttpJimuJobLog;
import com.jimu.http.service.HttpJimuJobLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.Trigger;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpJimuSchedulerTest {

    @Test
    void scheduledTaskStoreShouldBeConcurrentMap() throws Exception {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        HttpJimuEngine engine = mock(HttpJimuEngine.class);
        HttpJimuJobLogService jobLogService = mock(HttpJimuJobLogService.class);
        HttpJimuScheduler scheduler = new HttpJimuScheduler(taskScheduler, engine, jobLogService, new JimuProperties());

        Field f = HttpJimuScheduler.class.getDeclaredField("scheduledTasks");
        f.setAccessible(true);
        Object value = f.get(scheduler);

        assertTrue(value instanceof ConcurrentHashMap);
        assertTrue(value instanceof Map);
    }

    @Test
    void shouldSkipWhenConfigIsNull() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        HttpJimuScheduler scheduler = new HttpJimuScheduler(taskScheduler, mock(HttpJimuEngine.class), mock(HttpJimuJobLogService.class), new JimuProperties());
        scheduler.schedule(null);
        verify(taskScheduler, never()).schedule(org.mockito.ArgumentMatchers.any(Runnable.class), org.mockito.ArgumentMatchers.any(Trigger.class));
    }

    @Test
    void shouldSkipWhenJobDisabled() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        HttpJimuScheduler scheduler = new HttpJimuScheduler(taskScheduler, mock(HttpJimuEngine.class), mock(HttpJimuJobLogService.class), new JimuProperties());
        HttpJimuConfig config = new HttpJimuConfig();
        config.setId("id-1");
        config.setEnableJob(false);
        config.setCronConfig("0/5 * * * * ?");
        scheduler.schedule(config);
        verify(taskScheduler, never()).schedule(org.mockito.ArgumentMatchers.any(Runnable.class), org.mockito.ArgumentMatchers.any(Trigger.class));
    }

    @Test
    void shouldScheduleWithTrimmedCron() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        HttpJimuScheduler scheduler = new HttpJimuScheduler(taskScheduler, mock(HttpJimuEngine.class), mock(HttpJimuJobLogService.class), new JimuProperties());
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        when(taskScheduler.schedule(org.mockito.ArgumentMatchers.any(Runnable.class), org.mockito.ArgumentMatchers.any(Trigger.class)))
                .thenAnswer(invocation -> future);

        HttpJimuConfig config = new HttpJimuConfig();
        config.setId("id-2");
        config.setEnableJob(true);
        config.setCronConfig(" 0/10 * * * * ? ");

        scheduler.schedule(config);

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(org.mockito.ArgumentMatchers.any(Runnable.class), triggerCaptor.capture());
        assertEquals("0/10 * * * * ?", triggerCaptor.getValue().toString());
    }

    @Test
    void shouldCancelScheduledFuture() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        HttpJimuScheduler scheduler = new HttpJimuScheduler(taskScheduler, mock(HttpJimuEngine.class), mock(HttpJimuJobLogService.class), new JimuProperties());
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        when(taskScheduler.schedule(org.mockito.ArgumentMatchers.any(Runnable.class), org.mockito.ArgumentMatchers.any(Trigger.class)))
                .thenAnswer(invocation -> future);

        HttpJimuConfig config = new HttpJimuConfig();
        config.setId("id-3");
        config.setEnableJob(true);
        config.setCronConfig("0/15 * * * * ?");
        scheduler.schedule(config);
        scheduler.cancel("id-3");

        verify(future).cancel(true);
    }

    @Test
    void shouldRunScheduledTaskAndWriteSuccessLog() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        HttpJimuEngine engine = mock(HttpJimuEngine.class);
        HttpJimuJobLogService jobLogService = mock(HttpJimuJobLogService.class);
        HttpJimuScheduler scheduler = new HttpJimuScheduler(taskScheduler, engine, jobLogService, new JimuProperties());
        AtomicReference<Runnable> runnableRef = new AtomicReference<>();

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenAnswer(invocation -> {
            runnableRef.set(invocation.getArgument(0));
            return future;
        });
        when(engine.execute(any(HttpJimuConfig.class), any(Map.class))).thenReturn("ok");

        HttpJimuConfig config = new HttpJimuConfig();
        config.setId("id-4");
        config.setHttpId("h-4");
        config.setName("task-4");
        config.setEnableJob(true);
        config.setCronConfig("0/30 * * * * ?");
        scheduler.schedule(config);

        assertNotNull(runnableRef.get());
        runnableRef.get().run();

        ArgumentCaptor<HttpJimuJobLog> logCaptor = ArgumentCaptor.forClass(HttpJimuJobLog.class);
        verify(jobLogService).saveAsync(logCaptor.capture());
        assertEquals("SUCCESS", logCaptor.getValue().getStatus());
        assertEquals("h-4", logCaptor.getValue().getHttpId());
    }

    @Test
    void shouldRunScheduledTaskAndWriteErrorLogWhenEngineThrows() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        HttpJimuEngine engine = mock(HttpJimuEngine.class);
        HttpJimuJobLogService jobLogService = mock(HttpJimuJobLogService.class);
        HttpJimuScheduler scheduler = new HttpJimuScheduler(taskScheduler, engine, jobLogService, new JimuProperties());
        AtomicReference<Runnable> runnableRef = new AtomicReference<>();

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenAnswer(invocation -> {
            runnableRef.set(invocation.getArgument(0));
            return future;
        });
        when(engine.execute(any(HttpJimuConfig.class), any(Map.class))).thenThrow(new RuntimeException("boom"));

        HttpJimuConfig config = new HttpJimuConfig();
        config.setId("id-5");
        config.setHttpId("h-5");
        config.setName("task-5");
        config.setEnableJob(true);
        config.setCronConfig("0/45 * * * * ?");
        scheduler.schedule(config);
        runnableRef.get().run();

        ArgumentCaptor<HttpJimuJobLog> logCaptor = ArgumentCaptor.forClass(HttpJimuJobLog.class);
        verify(jobLogService).saveAsync(logCaptor.capture());
        assertEquals("ERROR", logCaptor.getValue().getStatus());
        assertTrue(logCaptor.getValue().getErrorMsg().contains("boom"));
    }

    @Test
    void shouldIgnoreInvalidCronWithoutThrowing() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        HttpJimuScheduler scheduler = new HttpJimuScheduler(taskScheduler, mock(HttpJimuEngine.class), mock(HttpJimuJobLogService.class), new JimuProperties());
        HttpJimuConfig config = new HttpJimuConfig();
        config.setId("id-6");
        config.setEnableJob(true);
        config.setCronConfig("invalid cron");
        scheduler.schedule(config);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void shouldCreateTaskSchedulerBean() {
        HttpJimuScheduler.SchedulerConfig schedulerConfig = new HttpJimuScheduler.SchedulerConfig();
        TaskScheduler scheduler = schedulerConfig.taskScheduler(new JimuProperties());
        assertTrue(scheduler instanceof ThreadPoolTaskScheduler);
    }
}
