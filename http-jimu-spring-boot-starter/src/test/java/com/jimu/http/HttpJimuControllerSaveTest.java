package com.jimu.http;

import com.jimu.http.controller.HttpJimuController;
import com.jimu.http.engine.HttpJimuEngine;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.model.Result;
import com.jimu.http.service.HttpJimuJobLogService;
import com.jimu.http.service.HttpJimuPoolService;
import com.jimu.http.service.HttpJimuScriptMetaService;
import com.jimu.http.service.HttpJimuService;
import com.jimu.http.service.HttpJimuStepService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpJimuControllerSaveTest {

    private HttpJimuController newController(HttpJimuService httpJimuService) {
        return new HttpJimuController(
                httpJimuService,
                mock(HttpJimuJobLogService.class),
                mock(HttpJimuStepService.class),
                mock(HttpJimuPoolService.class),
                mock(HttpJimuEngine.class),
                mock(HttpJimuScriptMetaService.class)
        );
    }

    @Test
    void shouldRejectInvalidCronOnSave() {
        HttpJimuService service = mock(HttpJimuService.class);
        HttpJimuController controller = newController(service);
        HttpJimuConfig config = new HttpJimuConfig();
        config.setEnableJob(true);
        config.setCronConfig("bad cron");

        Result<Boolean> result = controller.save(config);

        assertEquals(1100, result.getCode());
        assertTrue(result.getMsg().contains("Cron"));
        verify(service, never()).saveOrUpdate(org.mockito.ArgumentMatchers.any(HttpJimuConfig.class));
    }

    @Test
    void shouldTrimCronAndCallServiceOnSave() {
        HttpJimuService service = mock(HttpJimuService.class);
        when(service.saveOrUpdate(org.mockito.ArgumentMatchers.any(HttpJimuConfig.class))).thenReturn(true);
        HttpJimuController controller = newController(service);
        HttpJimuConfig config = new HttpJimuConfig();
        config.setEnableJob(true);
        config.setCronConfig(" 0/5 * * * * ? ");
        config.setUrl("https://example.com/api");
        config.setHeaders("[]");
        config.setQueryParams("[]");
        config.setBodyConfig("{}");
        config.setStepsConfig("[]");
        config.setParamsConfig("{}");

        Result<Boolean> result = controller.save(config);

        assertEquals(1000, result.getCode());
        assertEquals(Boolean.TRUE, result.getData());
        assertNotNull(config.getUpdateTime());

        ArgumentCaptor<HttpJimuConfig> captor = ArgumentCaptor.forClass(HttpJimuConfig.class);
        verify(service).saveOrUpdate(captor.capture());
        assertEquals("0/5 * * * * ?", captor.getValue().getCronConfig());
    }
}
