package com.jimu.http.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StepTarget {
    BODY("BODY"),
    HEADER("HEADER"),
    QUERY("QUERY"),
    FORM("FORM"),
    RESPONSE_BODY("RESPONSE_BODY"),
    RESPONSE_HEADER("RESPONSE_HEADER"),
    RESPONSE_STATUS("RESPONSE_STATUS");

    private final String code;

    public static StepTarget fromCode(String code) {
        if (code == null) return BODY; // Default
        for (StepTarget target : values()) {
            if (target.code.equalsIgnoreCase(code)) {
                return target;
            }
        }
        return BODY;
    }
    
    public boolean isResponse() {
        return this == RESPONSE_BODY || this == RESPONSE_HEADER || this == RESPONSE_STATUS;
    }
}
