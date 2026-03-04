package com.jimu.http.engine.model;

import lombok.Data;

@Data
public class StepTrace {
    private Integer stepIndex;
    private String phase;
    private String stepType;
    private String target;
    private String inputSnapshot;
    private String outputSnapshot;
}
