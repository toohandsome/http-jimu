package com.jimu.http.controller;

import com.jimu.http.engine.model.ExecuteDetail;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.service.HttpJimuService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class HttpJimuExposedApiEndpoint {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "content-length", "host"
    );

    private final HttpJimuService httpJimuService;

    public ResponseEntity<String> handle(HttpServletRequest request) throws IOException {
        Object attr = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = attr != null ? String.valueOf(attr) : request.getRequestURI();
        HttpJimuConfig config = httpJimuService.getByExposedPathAndMethod(path, request.getMethod());
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> inputParams = HttpJimuExposedRequestResolver.resolve(
                request,
                config.getExposedParamType(),
                config.getExposedMappingConfig()
        );
        ExecuteDetail detail = httpJimuService.callWithDetail(config, inputParams);

        HttpHeaders responseHeaders = new HttpHeaders();
        if (detail.getResponseHeaders() != null) {
            detail.getResponseHeaders().forEach((key, value) -> {
                if (key == null || HOP_BY_HOP_HEADERS.contains(key.trim().toLowerCase())) {
                    return;
                }
                responseHeaders.put(key, List.of(value));
            });
        }
        int statusCode = detail.getResponseStatus() > 0 ? detail.getResponseStatus() : 200;
        return new ResponseEntity<>(detail.getResponseBody(), responseHeaders, HttpStatusCode.valueOf(statusCode));
    }
}
