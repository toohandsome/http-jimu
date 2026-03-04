package com.jimu.http.engine.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class PreviewDetail {
    private String requestMethod;
    private String requestUrl;
    private Map<String, String> requestHeaders = new LinkedHashMap<>();
    private String requestBody;
    private List<StepTrace> stepTraces = new ArrayList<>();
}
