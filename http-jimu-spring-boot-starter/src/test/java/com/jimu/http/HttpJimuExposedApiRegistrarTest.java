package com.jimu.http;

import com.jimu.http.controller.HttpJimuExposedApiEndpoint;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.service.HttpJimuExposedApiRegistrar;
import com.jimu.http.service.HttpJimuService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpJimuExposedApiRegistrarTest {

    @Test
    void shouldRegisterAndUnregisterExposedApi() {
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        HttpJimuExposedApiEndpoint endpoint = mock(HttpJimuExposedApiEndpoint.class);
        HttpJimuService service = mock(HttpJimuService.class);
        when(service.list()).thenReturn(java.util.List.of());
        HttpJimuExposedApiRegistrar registrar = new HttpJimuExposedApiRegistrar(handlerMapping, endpoint, service);

        HttpJimuConfig config = new HttpJimuConfig();
        config.setId("cfg1");
        config.setHttpId("demo");
        config.setExposeApi(true);
        config.setExposedPath("/open/demo/{id}");
        config.setExposedMethod("GET");
        config.setExposedParamType("AUTO");

        registrar.refresh(config);

        verify(handlerMapping).registerMapping(any(RequestMappingInfo.class), eq(endpoint), any());
        registrar.unregister("cfg1");
        verify(handlerMapping).unregisterMapping(any(RequestMappingInfo.class));
    }

    @Test
    void shouldSkipInvalidExposedApi() {
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        HttpJimuExposedApiRegistrar registrar = new HttpJimuExposedApiRegistrar(
                handlerMapping,
                mock(HttpJimuExposedApiEndpoint.class),
                mock(HttpJimuService.class)
        );
        HttpJimuConfig config = new HttpJimuConfig();
        config.setId("cfg2");
        config.setExposeApi(true);
        config.setExposedMethod("GET");

        registrar.refresh(config);

        verify(handlerMapping, never()).registerMapping(any(RequestMappingInfo.class), any(), any());
    }
}
