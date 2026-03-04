package com.jimu.http.engine.step;

import com.jimu.http.model.enums.StepType;
import java.util.Map;

public interface StepProcessor {
    StepType getType();
    Object process(Object target, Map<String, Object> config, StepContext stepContext);
}
