-- PostgreSQL 13+ schema for HTTP Jimu（含中文表/字段注释）

CREATE TABLE IF NOT EXISTS http_jimu_config (
    id VARCHAR(64) PRIMARY KEY,
    http_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100),
    url VARCHAR(1000) NOT NULL,
    method VARCHAR(10) NOT NULL DEFAULT 'POST',
    headers TEXT,
    query_params TEXT,
    body_config TEXT,
    body_type VARCHAR(20) NOT NULL DEFAULT 'none',
    body_raw_type VARCHAR(20) NOT NULL DEFAULT 'json',
    cron_config VARCHAR(500),
    enable_job BOOLEAN NOT NULL DEFAULT FALSE,
    params_config TEXT,
    steps_config TEXT,
    pool_id VARCHAR(64),
    connect_timeout INTEGER,
    read_timeout INTEGER,
    write_timeout INTEGER,
    call_timeout INTEGER,
    retry_on_connection_failure BOOLEAN,
    follow_redirects BOOLEAN,
    follow_ssl_redirects BOOLEAN,
    proxy_host VARCHAR(255),
    proxy_port INTEGER,
    proxy_type VARCHAR(20),
    retry_max_attempts INTEGER,
    retry_on_http_status VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE http_jimu_config ADD COLUMN IF NOT EXISTS retry_max_attempts INTEGER;
ALTER TABLE http_jimu_config ADD COLUMN IF NOT EXISTS retry_on_http_status VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_http_jimu_config_enable_job ON http_jimu_config(enable_job);

CREATE TABLE IF NOT EXISTS http_jimu_job_log (
    id BIGSERIAL PRIMARY KEY,
    config_id VARCHAR(64) NOT NULL,
    http_id VARCHAR(64) NOT NULL,
    input_params TEXT,
    output_result TEXT,
    status VARCHAR(20),
    error_msg TEXT,
    duration BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_http_jimu_job_log_config_id ON http_jimu_job_log(config_id);
CREATE INDEX IF NOT EXISTS idx_http_jimu_job_log_create_time ON http_jimu_job_log(create_time);

CREATE TABLE IF NOT EXISTS http_jimu_step (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    target VARCHAR(32) NOT NULL DEFAULT 'BODY',
    script_content TEXT,
    config_json TEXT,
    input_schema TEXT,
    output_schema TEXT,
    description VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS http_jimu_pool (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    max_idle_connections INTEGER NOT NULL DEFAULT 5,
    keep_alive_duration BIGINT NOT NULL DEFAULT 300000,
    connect_timeout INTEGER NOT NULL DEFAULT 10000,
    read_timeout INTEGER NOT NULL DEFAULT 10000,
    write_timeout INTEGER NOT NULL DEFAULT 10000,
    call_timeout INTEGER NOT NULL DEFAULT 0,
    retry_on_connection_failure BOOLEAN NOT NULL DEFAULT TRUE,
    follow_redirects BOOLEAN NOT NULL DEFAULT TRUE,
    follow_ssl_redirects BOOLEAN NOT NULL DEFAULT TRUE,
    retry_max_attempts INTEGER NOT NULL DEFAULT 0,
    retry_on_http_status VARCHAR(255),
    max_requests INTEGER NOT NULL DEFAULT 64,
    max_requests_per_host INTEGER NOT NULL DEFAULT 5,
    ping_interval INTEGER NOT NULL DEFAULT 0,
    proxy_host VARCHAR(255),
    proxy_port INTEGER,
    proxy_type VARCHAR(20),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE http_jimu_pool ADD COLUMN IF NOT EXISTS retry_max_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE http_jimu_pool ADD COLUMN IF NOT EXISTS retry_on_http_status VARCHAR(255);

-- ShedLock table for JDBC distributed lock fallback
CREATE TABLE IF NOT EXISTS jimu_shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

COMMENT ON TABLE http_jimu_config IS 'HTTP积木接口配置表';
COMMENT ON COLUMN http_jimu_config.id IS '主键ID';
COMMENT ON COLUMN http_jimu_config.http_id IS '接口唯一标识';
COMMENT ON COLUMN http_jimu_config.name IS '接口名称';
COMMENT ON COLUMN http_jimu_config.url IS '请求URL';
COMMENT ON COLUMN http_jimu_config.method IS 'HTTP方法';
COMMENT ON COLUMN http_jimu_config.headers IS '请求头JSON';
COMMENT ON COLUMN http_jimu_config.query_params IS '查询参数JSON';
COMMENT ON COLUMN http_jimu_config.body_config IS '请求体配置JSON/原文';
COMMENT ON COLUMN http_jimu_config.body_type IS '请求体类型';
COMMENT ON COLUMN http_jimu_config.body_raw_type IS 'raw子类型';
COMMENT ON COLUMN http_jimu_config.cron_config IS 'Cron表达式';
COMMENT ON COLUMN http_jimu_config.enable_job IS '是否启用定时任务';
COMMENT ON COLUMN http_jimu_config.params_config IS '参数映射配置JSON';
COMMENT ON COLUMN http_jimu_config.steps_config IS '积木步骤配置JSON';
COMMENT ON COLUMN http_jimu_config.pool_id IS '连接池ID';
COMMENT ON COLUMN http_jimu_config.connect_timeout IS '连接超时毫秒';
COMMENT ON COLUMN http_jimu_config.read_timeout IS '读取超时毫秒';
COMMENT ON COLUMN http_jimu_config.write_timeout IS '写入超时毫秒';
COMMENT ON COLUMN http_jimu_config.call_timeout IS '请求总超时毫秒';
COMMENT ON COLUMN http_jimu_config.retry_on_connection_failure IS '是否连接失败自动重试';
COMMENT ON COLUMN http_jimu_config.follow_redirects IS '是否跟随HTTP重定向';
COMMENT ON COLUMN http_jimu_config.follow_ssl_redirects IS '是否跟随SSL重定向';
COMMENT ON COLUMN http_jimu_config.proxy_host IS '代理主机';
COMMENT ON COLUMN http_jimu_config.proxy_port IS '代理端口';
COMMENT ON COLUMN http_jimu_config.proxy_type IS '代理类型(HTTP/SOCKS)';
COMMENT ON COLUMN http_jimu_config.retry_max_attempts IS '业务级HTTP状态码重试次数';
COMMENT ON COLUMN http_jimu_config.retry_on_http_status IS '触发重试的HTTP状态码列表';
COMMENT ON COLUMN http_jimu_config.create_time IS '创建时间';
COMMENT ON COLUMN http_jimu_config.update_time IS '更新时间';

COMMENT ON TABLE http_jimu_job_log IS 'HTTP积木任务执行日志表';
COMMENT ON COLUMN http_jimu_job_log.id IS '主键ID';
COMMENT ON COLUMN http_jimu_job_log.config_id IS '配置ID';
COMMENT ON COLUMN http_jimu_job_log.http_id IS '接口唯一标识';
COMMENT ON COLUMN http_jimu_job_log.input_params IS '执行入参JSON';
COMMENT ON COLUMN http_jimu_job_log.output_result IS '执行结果';
COMMENT ON COLUMN http_jimu_job_log.status IS '执行状态';
COMMENT ON COLUMN http_jimu_job_log.error_msg IS '错误信息';
COMMENT ON COLUMN http_jimu_job_log.duration IS '耗时毫秒';
COMMENT ON COLUMN http_jimu_job_log.create_time IS '创建时间';

COMMENT ON TABLE http_jimu_step IS 'HTTP积木步骤模板表';
COMMENT ON COLUMN http_jimu_step.id IS '主键ID';
COMMENT ON COLUMN http_jimu_step.code IS '步骤编码';
COMMENT ON COLUMN http_jimu_step.name IS '步骤名称';
COMMENT ON COLUMN http_jimu_step.type IS '步骤类型';
COMMENT ON COLUMN http_jimu_step.target IS '作用目标';
COMMENT ON COLUMN http_jimu_step.script_content IS '脚本内容';
COMMENT ON COLUMN http_jimu_step.config_json IS '步骤配置JSON';
COMMENT ON COLUMN http_jimu_step.input_schema IS '输入结构描述';
COMMENT ON COLUMN http_jimu_step.output_schema IS '输出结构描述';
COMMENT ON COLUMN http_jimu_step.description IS '步骤说明';
COMMENT ON COLUMN http_jimu_step.create_time IS '创建时间';
COMMENT ON COLUMN http_jimu_step.update_time IS '更新时间';

COMMENT ON TABLE http_jimu_pool IS 'HTTP客户端连接池配置表';
COMMENT ON COLUMN http_jimu_pool.id IS '主键ID';
COMMENT ON COLUMN http_jimu_pool.name IS '连接池名称';
COMMENT ON COLUMN http_jimu_pool.max_idle_connections IS '最大空闲连接数';
COMMENT ON COLUMN http_jimu_pool.keep_alive_duration IS '连接保活时长毫秒';
COMMENT ON COLUMN http_jimu_pool.connect_timeout IS '连接超时毫秒';
COMMENT ON COLUMN http_jimu_pool.read_timeout IS '读取超时毫秒';
COMMENT ON COLUMN http_jimu_pool.write_timeout IS '写入超时毫秒';
COMMENT ON COLUMN http_jimu_pool.call_timeout IS '请求总超时毫秒';
COMMENT ON COLUMN http_jimu_pool.retry_on_connection_failure IS '是否连接失败自动重试';
COMMENT ON COLUMN http_jimu_pool.follow_redirects IS '是否跟随HTTP重定向';
COMMENT ON COLUMN http_jimu_pool.follow_ssl_redirects IS '是否跟随SSL重定向';
COMMENT ON COLUMN http_jimu_pool.retry_max_attempts IS '业务级HTTP状态码重试次数';
COMMENT ON COLUMN http_jimu_pool.retry_on_http_status IS '触发重试的HTTP状态码列表';
COMMENT ON COLUMN http_jimu_pool.max_requests IS '全局最大并发请求数';
COMMENT ON COLUMN http_jimu_pool.max_requests_per_host IS '每个主机最大并发请求数';
COMMENT ON COLUMN http_jimu_pool.ping_interval IS 'HTTP2心跳间隔毫秒';
COMMENT ON COLUMN http_jimu_pool.proxy_host IS '代理主机';
COMMENT ON COLUMN http_jimu_pool.proxy_port IS '代理端口';
COMMENT ON COLUMN http_jimu_pool.proxy_type IS '代理类型(HTTP/SOCKS)';
COMMENT ON COLUMN http_jimu_pool.create_time IS '创建时间';
COMMENT ON COLUMN http_jimu_pool.update_time IS '更新时间';

COMMENT ON TABLE jimu_shedlock IS 'ShedLock分布式锁表';
COMMENT ON COLUMN jimu_shedlock.name IS '锁名称';
COMMENT ON COLUMN jimu_shedlock.lock_until IS '锁过期时间';
COMMENT ON COLUMN jimu_shedlock.locked_at IS '加锁时间';
COMMENT ON COLUMN jimu_shedlock.locked_by IS '持锁实例标识';
