package com.jimu.http.engine.step;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class StepContext {
    private Map<String, Object> context;
    private String url;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
}
