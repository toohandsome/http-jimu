package com.jimu.http.dto.script;

import lombok.Data;

@Data
public class BeanMetaCacheEntry {
    private BeanMetaDetail detail;
    private long expireAt;
}
