-- MySQL 8+ schema for HTTP Jimu（含中文表/字段注释）

CREATE TABLE IF NOT EXISTS http_jimu_config (
    id VARCHAR(64) NOT NULL COMMENT '主键ID',
    http_id VARCHAR(64) NOT NULL COMMENT '接口唯一标识',
    name VARCHAR(100) NULL COMMENT '接口名称',
    url VARCHAR(1000) NOT NULL COMMENT '请求URL',
    method VARCHAR(10) NOT NULL DEFAULT 'POST' COMMENT 'HTTP方法',
    headers LONGTEXT NULL COMMENT '请求头JSON',
    query_params LONGTEXT NULL COMMENT '查询参数JSON',
    body_config LONGTEXT NULL COMMENT '请求体配置JSON/原文',
    body_type VARCHAR(20) NOT NULL DEFAULT 'none' COMMENT '请求体类型',
    body_raw_type VARCHAR(20) NOT NULL DEFAULT 'json' COMMENT 'raw子类型',
    cron_config VARCHAR(500) NULL COMMENT 'Cron表达式',
    enable_job TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用定时任务',
    params_config LONGTEXT NULL COMMENT '参数映射配置JSON',
    steps_config LONGTEXT NULL COMMENT '积木步骤配置JSON',
    pool_id VARCHAR(64) NULL COMMENT '连接池ID',
    connect_timeout INT NULL COMMENT '连接超时毫秒',
    read_timeout INT NULL COMMENT '读取超时毫秒',
    write_timeout INT NULL COMMENT '写入超时毫秒',
    call_timeout INT NULL COMMENT '请求总超时毫秒',
    retry_on_connection_failure TINYINT(1) NULL COMMENT '是否连接失败自动重试',
    follow_redirects TINYINT(1) NULL COMMENT '是否跟随HTTP重定向',
    follow_ssl_redirects TINYINT(1) NULL COMMENT '是否跟随SSL重定向',
    dns_overrides TEXT NULL COMMENT 'DNS覆盖配置(JSON)',
    proxy_host VARCHAR(255) NULL COMMENT '代理主机',
    proxy_port INT NULL COMMENT '代理端口',
    proxy_type VARCHAR(20) NULL COMMENT '代理类型(HTTP/SOCKS)',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_http_jimu_config_http_id (http_id),
    KEY idx_http_jimu_config_enable_job (enable_job)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HTTP积木接口配置表';

CREATE TABLE IF NOT EXISTS http_jimu_job_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    config_id VARCHAR(64) NOT NULL COMMENT '配置ID',
    http_id VARCHAR(64) NOT NULL COMMENT '接口唯一标识',
    input_params LONGTEXT NULL COMMENT '执行入参JSON',
    output_result LONGTEXT NULL COMMENT '执行结果',
    status VARCHAR(20) NULL COMMENT '执行状态',
    error_msg LONGTEXT NULL COMMENT '错误信息',
    duration BIGINT NULL COMMENT '耗时毫秒',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_http_jimu_job_log_config_id (config_id),
    KEY idx_http_jimu_job_log_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HTTP积木任务执行日志表';

CREATE TABLE IF NOT EXISTS http_jimu_step (
    id VARCHAR(64) NOT NULL COMMENT '主键ID',
    code VARCHAR(64) NOT NULL COMMENT '步骤编码',
    name VARCHAR(100) NOT NULL COMMENT '步骤名称',
    type VARCHAR(20) NOT NULL COMMENT '步骤类型',
    target VARCHAR(32) NOT NULL DEFAULT 'BODY' COMMENT '作用目标',
    script_content LONGTEXT NULL COMMENT '脚本内容',
    config_json LONGTEXT NULL COMMENT '步骤配置JSON',
    input_schema LONGTEXT NULL COMMENT '输入结构描述',
    output_schema LONGTEXT NULL COMMENT '输出结构描述',
    description VARCHAR(500) NULL COMMENT '步骤说明',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_http_jimu_step_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HTTP积木步骤模板表';

CREATE TABLE IF NOT EXISTS http_jimu_pool (
    id VARCHAR(64) NOT NULL COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT '连接池名称',
    max_idle_connections INT NOT NULL DEFAULT 5 COMMENT '最大空闲连接数',
    keep_alive_duration BIGINT NOT NULL DEFAULT 300000 COMMENT '连接保活时长毫秒',
    connect_timeout INT NOT NULL DEFAULT 10000 COMMENT '连接超时毫秒',
    read_timeout INT NOT NULL DEFAULT 10000 COMMENT '读取超时毫秒',
    write_timeout INT NOT NULL DEFAULT 10000 COMMENT '写入超时毫秒',
    call_timeout INT NOT NULL DEFAULT 0 COMMENT '请求总超时毫秒',
    retry_on_connection_failure TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否连接失败自动重试',
    follow_redirects TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否跟随HTTP重定向',
    follow_ssl_redirects TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否跟随SSL重定向',
    max_requests INT NOT NULL DEFAULT 64 COMMENT '全局最大并发请求数',
    max_requests_per_host INT NOT NULL DEFAULT 5 COMMENT '每个主机最大并发请求数',
    ping_interval INT NOT NULL DEFAULT 0 COMMENT 'HTTP2心跳间隔毫秒',
    dns_overrides TEXT NULL COMMENT 'DNS覆盖配置(JSON)',
    proxy_host VARCHAR(255) NULL COMMENT '代理主机',
    proxy_port INT NULL COMMENT '代理端口',
    proxy_type VARCHAR(20) NULL COMMENT '代理类型(HTTP/SOCKS)',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_http_jimu_pool_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HTTP客户端连接池配置表';

-- ShedLock table for JDBC distributed lock fallback
CREATE TABLE IF NOT EXISTS jimu_shedlock (
    name VARCHAR(64) NOT NULL COMMENT '锁名称',
    lock_until TIMESTAMP(3) NOT NULL COMMENT '锁过期时间',
    locked_at TIMESTAMP(3) NOT NULL COMMENT '加锁时间',
    locked_by VARCHAR(255) NOT NULL COMMENT '持锁实例标识',
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ShedLock分布式锁表';
