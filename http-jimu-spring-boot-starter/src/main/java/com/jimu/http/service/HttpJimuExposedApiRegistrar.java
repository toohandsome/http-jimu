package com.jimu.http.service;

import com.jimu.http.controller.HttpJimuExposedApiEndpoint;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.service.event.HttpJimuExposedApiRefreshEvent;
import com.jimu.http.service.event.HttpJimuExposedApiRemoveEvent;
import com.jimu.http.support.HttpJimuConfigSupport;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpJimuExposedApiRegistrar {

    private final RequestMappingHandlerMapping handlerMapping;
    private final HttpJimuExposedApiEndpoint endpoint;
    private final HttpJimuService httpJimuService;
    private final Map<String, RegisteredApi> registeredApis = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        httpJimuService.list().forEach(this::refresh);
    }

    @EventListener
    public void onRefresh(HttpJimuExposedApiRefreshEvent event) {
        if (event != null) {
            refresh(event.config());
        }
    }

    @EventListener
    public void onRemove(HttpJimuExposedApiRemoveEvent event) {
        if (event != null) {
            unregister(event.configId());
        }
    }

    public void refresh(HttpJimuConfig config) {
        if (config == null || config.getId() == null) {
            return;
        }
        unregister(config.getId());
        String error = HttpJimuConfigSupport.validateAndNormalizeExposedApi(config);
        if (!Boolean.TRUE.equals(config.getExposeApi()) || error != null) {
            if (error != null) {
                log.warn("Skip exposed api registration for config {}: {}", config.getHttpId(), error);
            }
            return;
        }
        try {
            Method method = HttpJimuExposedApiEndpoint.class.getMethod("handle", HttpServletRequest.class);
            RequestMappingInfo mappingInfo = RequestMappingInfo
                    .paths(config.getExposedPath())
                    .methods(RequestMethod.valueOf(config.getExposedMethod()))
                    .build();
            handlerMapping.registerMapping(mappingInfo, endpoint, method);
            registeredApis.put(config.getId(), new RegisteredApi(mappingInfo, config));
            log.info("Registered exposed api [{} {}] for httpId={}",
                    config.getExposedMethod(), config.getExposedPath(), config.getHttpId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register exposed api: " + config.getExposedPath(), e);
        }
    }

    public void unregister(String configId) {
        if (configId == null || configId.isBlank()) {
            return;
        }
        RegisteredApi existing = registeredApis.remove(configId);
        if (existing == null) {
            return;
        }
        handlerMapping.unregisterMapping(existing.mappingInfo());
        log.info("Unregistered exposed api [{} {}] for httpId={}",
                existing.config().getExposedMethod(), existing.config().getExposedPath(), existing.config().getHttpId());
    }

    private record RegisteredApi(RequestMappingInfo mappingInfo, HttpJimuConfig config) {
    }
}
