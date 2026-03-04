package com.jimu.http.service.cache;

import com.jimu.http.entity.HttpJimuConfig;
import lombok.Data;

@Data
public class HttpJimuConfigCacheEntry {
    private HttpJimuConfig config;
    private long expireAt;
}
