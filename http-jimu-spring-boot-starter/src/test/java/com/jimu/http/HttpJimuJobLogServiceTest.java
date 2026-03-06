package com.jimu.http;

import com.jimu.http.entity.HttpJimuJobLog;
import com.jimu.http.service.HttpJimuJobLogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class HttpJimuJobLogServiceTest {

    @Test
    void shouldSaveAsyncUsingConfiguredExecutor() {
        HttpJimuJobLogService service = spy(new HttpJimuJobLogService());
        ReflectionTestUtils.setField(service, "logSaveExecutor", (java.util.concurrent.Executor) Runnable::run);
        doReturn(true).when(service).save(any(HttpJimuJobLog.class));

        HttpJimuJobLog logEntry = HttpJimuJobLog.builder().httpId("h1").build();
        service.saveAsync(logEntry);

        verify(service).save(logEntry);
    }
}
