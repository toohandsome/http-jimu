package com.jimu.http.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BodyType {
    NONE("none"),
    FORM_DATA("form-data"),
    X_WWW_FORM_URLENCODED("x-www-form-urlencoded"),
    RAW("raw");

    private final String code;

    public static BodyType fromCode(String code) {
        if (code == null) return NONE;
        for (BodyType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return NONE;
    }
}
