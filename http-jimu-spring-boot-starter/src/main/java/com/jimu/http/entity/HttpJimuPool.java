package com.jimu.http.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("http_jimu_pool")
public class HttpJimuPool implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;

    /**
     * Max idle connections
     */
    private Integer maxIdleConnections;

    /**
     * Keep alive duration in milliseconds
     */
    private Long keepAliveDuration;

    /**
     * Connection timeout in milliseconds
     */
    private Integer connectTimeout;

    /**
     * Read timeout in milliseconds
     */
    private Integer readTimeout;

    /**
     * Write timeout in milliseconds
     */
    private Integer writeTimeout;

    /**
     * Overall call timeout in milliseconds
     */
    private Integer callTimeout;

    /**
     * Retry on connection failure
     */
    private Boolean retryOnConnectionFailure;

    /**
     * Follow HTTP redirects
     */
    private Boolean followRedirects;

    /**
     * Follow HTTPS to HTTP/HTTPS redirects
     */
    private Boolean followSslRedirects;

    /**
     * Dispatcher max requests
     */
    private Integer maxRequests;

    /**
     * Dispatcher max requests per host
     */
    private Integer maxRequestsPerHost;

    /**
     * HTTP/2 ping interval in milliseconds
     */
    private Integer pingInterval;

    /**
     * DNS override JSON, e.g. {"api.example.com":"1.1.1.1"}
     */
    private String dnsOverrides;

    /**
     * Proxy host
     */
    private String proxyHost;

    /**
     * Proxy port
     */
    private Integer proxyPort;

    /**
     * Proxy type: HTTP or SOCKS
     */
    private String proxyType;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
