package com.jimu.http.engine.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ExecuteDetail {
    private String requestMethod;
    private String requestUrl;
    private Map<String, String> requestHeaders = new LinkedHashMap<>();
    private String requestBody;
    private Integer responseStatus;
    private Map<String, String> responseHeaders = new LinkedHashMap<>();
    private String responseBody;
    private Long durationMs;
    private List<StepTrace> stepTraces = new ArrayList<>();
}
