package com.jimu.http.dto.script;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ScriptMeta {
    private List<MetaVariable> variables = new ArrayList<>();
    private List<MetaFunction> functions = new ArrayList<>();
    private Map<String, List<MetaMethod>> members = new HashMap<>();
    private List<MetaClass> classes = new ArrayList<>();
    private List<MetaBean> beans = new ArrayList<>();
}
