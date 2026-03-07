package com.jimu.http.service.event;

import com.jimu.http.entity.HttpJimuConfig;

public record HttpJimuExposedApiRefreshEvent(HttpJimuConfig config) {
}
