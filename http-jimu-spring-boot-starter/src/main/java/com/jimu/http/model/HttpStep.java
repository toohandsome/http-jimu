package com.jimu.http.model;

import lombok.Data;

import java.util.Map;

@Data
public class HttpStep {
    private String type; // SORT, SIGN, ENCRYPT, ADD_FIXED, SCRIPT
    private String target; // HEADER, BODY, QUERY, FORM, RESPONSE_BODY, RESPONSE_HEADER, RESPONSE_STATUS
    private String stepCode; // Reference step code from step library
    private Map<String, Object> config;
    private boolean enableLog; // Whether to print step input/output logs
}
