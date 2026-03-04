package com.jimu.http.dto.script;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaVariable {
    private String name;
    private String type;
    private String description;
}
