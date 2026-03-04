package com.jimu.http.dto.script;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BeanMetaDetail {
    private String beanName;
    private String type;
    private List<MetaMethod> methods = new ArrayList<>();
}
