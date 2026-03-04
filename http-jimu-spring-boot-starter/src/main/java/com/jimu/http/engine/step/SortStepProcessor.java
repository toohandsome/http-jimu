package com.jimu.http.engine.step;

import com.jimu.http.model.enums.StepType;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.TreeMap;

@Component
public class SortStepProcessor implements StepProcessor {
    @Override
    public StepType getType() {
        return StepType.SORT;
    }

    @Override
    public Object process(Object target, Map<String, Object> config, StepContext stepContext) {
        if (target instanceof Map) {
            return new TreeMap<>((Map<String, Object>) target);
        }
        return target;
    }
}
