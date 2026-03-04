package com.jimu.http.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StepType {
    SORT("SORT", "Field Sorting"),
    SIGN("SIGN", "Signature Generation"),
    ENCRYPT("ENCRYPT", "Encryption"),
    ADD_FIXED("ADD_FIXED", "Add Fixed Parameters"),
    SCRIPT("SCRIPT", "Groovy Script");

    private final String code;
    private final String description;

    public static StepType fromCode(String code) {
        if (code == null) return null;
        for (StepType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
