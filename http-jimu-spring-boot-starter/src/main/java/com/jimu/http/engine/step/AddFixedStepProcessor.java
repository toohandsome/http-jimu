package com.jimu.http.engine.step;

import com.jimu.http.model.enums.StepType;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class AddFixedStepProcessor implements StepProcessor {
    @Override
    public StepType getType() {
        return StepType.ADD_FIXED;
    }

    @Override
    public Object process(Object target, Map<String, Object> config, StepContext stepContext) {
        if (target instanceof Map && config != null) {
            ((Map<String, Object>) target).putAll(config);
        }
        return target;
    }
}
