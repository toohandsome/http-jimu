package com.jimu.http.engine.model;


import com.jimu.http.model.HttpStep;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class PreparedExecution {
    private String method;
    private String url;
    private Map<String, String> headers = new LinkedHashMap<>();
    private Object body;
    private Map<String, Object> context = new HashMap<>();
    private List<HttpStep> resolvedSteps = new ArrayList<>();
    private List<StepTrace> stepTraces = new ArrayList<>();
}
