-- SQL Server schema for HTTP Jimu（含中文表注释）

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'http_jimu_config')
BEGIN
    CREATE TABLE http_jimu_config (
        id VARCHAR(64) NOT NULL PRIMARY KEY,
        http_id VARCHAR(64) NOT NULL UNIQUE,
        name NVARCHAR(100) NULL,
        url NVARCHAR(1000) NOT NULL,
        method VARCHAR(10) NOT NULL CONSTRAINT df_http_jimu_config_method DEFAULT 'POST',
        headers NVARCHAR(MAX) NULL,
        query_params NVARCHAR(MAX) NULL,
        body_config NVARCHAR(MAX) NULL,
        body_type VARCHAR(20) NOT NULL CONSTRAINT df_http_jimu_config_body_type DEFAULT 'none',
        body_raw_type VARCHAR(20) NOT NULL CONSTRAINT df_http_jimu_config_body_raw_type DEFAULT 'json',
        cron_config NVARCHAR(500) NULL,
        enable_job BIT NOT NULL CONSTRAINT df_http_jimu_config_enable_job DEFAULT 0,
        params_config NVARCHAR(MAX) NULL,
        steps_config NVARCHAR(MAX) NULL,
        pool_id VARCHAR(64) NULL,
        connect_timeout INT NULL,
        read_timeout INT NULL,
        write_timeout INT NULL,
        call_timeout INT NULL,
        retry_on_connection_failure BIT NULL,
        follow_redirects BIT NULL,
        follow_ssl_redirects BIT NULL,
        dns_overrides NVARCHAR(MAX) NULL,
        proxy_host VARCHAR(255) NULL,
        proxy_port INT NULL,
        proxy_type VARCHAR(20) NULL,
        create_time DATETIME2 NOT NULL CONSTRAINT df_http_jimu_config_create_time DEFAULT GETDATE(),
        update_time DATETIME2 NOT NULL CONSTRAINT df_http_jimu_config_update_time DEFAULT GETDATE()
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'idx_http_jimu_config_enable_job')
BEGIN
    CREATE INDEX idx_http_jimu_config_enable_job ON http_jimu_config(enable_job);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'http_jimu_job_log')
BEGIN
    CREATE TABLE http_jimu_job_log (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        config_id VARCHAR(64) NOT NULL,
        http_id VARCHAR(64) NOT NULL,
        input_params NVARCHAR(MAX) NULL,
        output_result NVARCHAR(MAX) NULL,
        status VARCHAR(20) NULL,
        error_msg NVARCHAR(MAX) NULL,
        duration BIGINT NULL,
        create_time DATETIME2 NOT NULL CONSTRAINT df_http_jimu_job_log_create_time DEFAULT GETDATE()
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('http_jimu_job_log') AND name = 'idx_http_jimu_job_log_config_id')
BEGIN
    CREATE INDEX idx_http_jimu_job_log_config_id ON http_jimu_job_log(config_id);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('http_jimu_job_log') AND name = 'idx_http_jimu_job_log_create_time')
BEGIN
    CREATE INDEX idx_http_jimu_job_log_create_time ON http_jimu_job_log(create_time);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'http_jimu_step')
BEGIN
    CREATE TABLE http_jimu_step (
        id VARCHAR(64) NOT NULL PRIMARY KEY,
        code VARCHAR(64) NOT NULL UNIQUE,
        name NVARCHAR(100) NOT NULL,
        type VARCHAR(20) NOT NULL,
        target VARCHAR(32) NOT NULL CONSTRAINT df_http_jimu_step_target DEFAULT 'BODY',
        script_content NVARCHAR(MAX) NULL,
        config_json NVARCHAR(MAX) NULL,
        input_schema NVARCHAR(MAX) NULL,
        output_schema NVARCHAR(MAX) NULL,
        description NVARCHAR(500) NULL,
        create_time DATETIME2 NOT NULL CONSTRAINT df_http_jimu_step_create_time DEFAULT GETDATE(),
        update_time DATETIME2 NOT NULL CONSTRAINT df_http_jimu_step_update_time DEFAULT GETDATE()
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'http_jimu_pool')
BEGIN
    CREATE TABLE http_jimu_pool (
        id VARCHAR(64) NOT NULL PRIMARY KEY,
        name NVARCHAR(100) NOT NULL UNIQUE,
        max_idle_connections INT NOT NULL CONSTRAINT df_http_jimu_pool_max_idle_connections DEFAULT 5,
        keep_alive_duration BIGINT NOT NULL CONSTRAINT df_http_jimu_pool_keep_alive_duration DEFAULT 300000,
        connect_timeout INT NOT NULL CONSTRAINT df_http_jimu_pool_connect_timeout DEFAULT 10000,
        read_timeout INT NOT NULL CONSTRAINT df_http_jimu_pool_read_timeout DEFAULT 10000,
        write_timeout INT NOT NULL CONSTRAINT df_http_jimu_pool_write_timeout DEFAULT 10000,
        call_timeout INT NOT NULL CONSTRAINT df_http_jimu_pool_call_timeout DEFAULT 0,
        retry_on_connection_failure BIT NOT NULL CONSTRAINT df_http_jimu_pool_retry_on_connection_failure DEFAULT 1,
        follow_redirects BIT NOT NULL CONSTRAINT df_http_jimu_pool_follow_redirects DEFAULT 1,
        follow_ssl_redirects BIT NOT NULL CONSTRAINT df_http_jimu_pool_follow_ssl_redirects DEFAULT 1,
        max_requests INT NOT NULL CONSTRAINT df_http_jimu_pool_max_requests DEFAULT 64,
        max_requests_per_host INT NOT NULL CONSTRAINT df_http_jimu_pool_max_requests_per_host DEFAULT 5,
        ping_interval INT NOT NULL CONSTRAINT df_http_jimu_pool_ping_interval DEFAULT 0,
        dns_overrides NVARCHAR(MAX) NULL,
        proxy_host VARCHAR(255) NULL,
        proxy_port INT NULL,
        proxy_type VARCHAR(20) NULL,
        create_time DATETIME2 NOT NULL CONSTRAINT df_http_jimu_pool_create_time DEFAULT GETDATE(),
        update_time DATETIME2 NOT NULL CONSTRAINT df_http_jimu_pool_update_time DEFAULT GETDATE()
    );
END
GO

-- ShedLock table for JDBC distributed lock fallback
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'jimu_shedlock')
BEGIN
    CREATE TABLE jimu_shedlock (
        name NVARCHAR(64) NOT NULL PRIMARY KEY,
        lock_until DATETIME2 NOT NULL,
        locked_at DATETIME2 NOT NULL,
        locked_by NVARCHAR(255) NOT NULL
    );
END
GO

-- ========== 中文表注释（Extended Property） ==========
IF NOT EXISTS (SELECT 1 FROM fn_listextendedproperty('MS_Description','SCHEMA','dbo','TABLE','http_jimu_config',NULL,NULL))
EXEC sp_addextendedproperty 'MS_Description', N'HTTP积木接口配置表', 'SCHEMA','dbo','TABLE','http_jimu_config';
GO
IF NOT EXISTS (SELECT 1 FROM fn_listextendedproperty('MS_Description','SCHEMA','dbo','TABLE','http_jimu_job_log',NULL,NULL))
EXEC sp_addextendedproperty 'MS_Description', N'HTTP积木任务执行日志表', 'SCHEMA','dbo','TABLE','http_jimu_job_log';
GO
IF NOT EXISTS (SELECT 1 FROM fn_listextendedproperty('MS_Description','SCHEMA','dbo','TABLE','http_jimu_step',NULL,NULL))
EXEC sp_addextendedproperty 'MS_Description', N'HTTP积木步骤模板表', 'SCHEMA','dbo','TABLE','http_jimu_step';
GO
IF NOT EXISTS (SELECT 1 FROM fn_listextendedproperty('MS_Description','SCHEMA','dbo','TABLE','http_jimu_pool',NULL,NULL))
EXEC sp_addextendedproperty 'MS_Description', N'HTTP客户端连接池配置表', 'SCHEMA','dbo','TABLE','http_jimu_pool';
GO
IF NOT EXISTS (SELECT 1 FROM fn_listextendedproperty('MS_Description','SCHEMA','dbo','TABLE','jimu_shedlock',NULL,NULL))
EXEC sp_addextendedproperty 'MS_Description', N'ShedLock分布式锁表', 'SCHEMA','dbo','TABLE','jimu_shedlock';
GO
