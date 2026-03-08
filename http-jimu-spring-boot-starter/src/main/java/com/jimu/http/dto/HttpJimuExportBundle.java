package com.jimu.http.dto;

import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.entity.HttpJimuGroup;
import com.jimu.http.entity.HttpJimuPool;
import com.jimu.http.entity.HttpJimuStep;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HttpJimuExportBundle {
    private String version = "1";
    private Long exportedAt;
    private List<HttpJimuGroup> groups = new ArrayList<>();
    private List<HttpJimuPool> pools = new ArrayList<>();
    private List<HttpJimuStep> steps = new ArrayList<>();
    private List<HttpJimuConfig> configs = new ArrayList<>();
}
