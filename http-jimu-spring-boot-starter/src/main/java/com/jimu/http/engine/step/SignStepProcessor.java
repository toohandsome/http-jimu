package com.jimu.http.engine.step;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.jimu.http.engine.support.JimuExpressionResolver;
import com.jimu.http.model.enums.StepType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SignStepProcessor implements StepProcessor {

    private final JimuExpressionResolver expressionResolver;

    @Override
    public StepType getType() {
        return StepType.SIGN;
    }

    @Override
    public Object process(Object target, Map<String, Object> config, StepContext stepContext) {
        if (!(target instanceof Map)) return target;
        if (config == null) config = Collections.emptyMap();
        Map<String, Object> map = (Map<String, Object>) target;

        String signType = (String) config.getOrDefault("algorithm", "MD5");
        String targetField = (String) config.getOrDefault("targetField", "sign");
        String salt = (String) config.getOrDefault("salt", "");
        salt = expressionResolver.resolve(salt, stepContext.getContext());

        String toSign = new TreeMap<>(map).entrySet().stream()
                .filter(e -> !e.getKey().equals(targetField))
                .map(e -> e.getKey() + "=" + (e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.joining("&"));

        if (StrUtil.isNotBlank(salt)) {
            toSign += salt;
        }

        if (StrUtil.isBlank(targetField)) {
            throw new IllegalArgumentException("SIGN config targetField cannot be blank");
        }

        String signature;
        if ("MD5".equalsIgnoreCase(signType)) {
            signature = SecureUtil.md5(toSign);
        } else if ("SHA256".equalsIgnoreCase(signType)) {
            signature = SecureUtil.sha256(toSign);
        } else {
            throw new IllegalArgumentException("Unsupported SIGN algorithm: " + signType);
        }

        map.put(targetField, signature);
        return map;
    }
}
