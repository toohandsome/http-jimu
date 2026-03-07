package com.jimu.http.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("http_jimu_config")
public class HttpJimuConfig {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String httpId;
    private String name;
    private String url;
    private String method;
    private String headers; // JSON string
    private String queryParams; // JSON string
    private String bodyConfig; // JSON string or raw content
    private String bodyType; // none, form-data, x-www-form-urlencoded, raw
    private String bodyRawType; // text, javascript, json, html, xml
    private String cronConfig; // cron expression
    private Boolean enableJob; // is job enabled
    private String paramsConfig; // JSON string
    private String stepsConfig; // JSON string
    private String poolId;
    private Integer connectTimeout;
    private Integer readTimeout;
    private Integer writeTimeout;
    private Integer callTimeout;
    private Boolean retryOnConnectionFailure;
    private Boolean followRedirects;
    private Boolean followSslRedirects;
    private String proxyHost;
    private Integer proxyPort;
    private String proxyType; // HTTP, SOCKS
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /**
     * Max retry attempts on configurable HTTP error status codes (0 = no retry).
     */
    private Integer retryMaxAttempts;
    /**
     * Comma-separated HTTP status codes that trigger a retry, e.g. "502,503,504".
     */
    private String retryOnHttpStatus;
}
