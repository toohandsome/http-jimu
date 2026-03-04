package com.jimu.http.dto.script;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaMethod {
    private String name;
    private String insertText;
    private String description;
    private List<String> parameters;
    private String returnType;
}
