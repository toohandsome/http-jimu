-- SQL Server Schema for HTTP Jimu
-- 1. Create http_jimu_config if not exists
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'http_jimu_config')
BEGIN
    CREATE TABLE http_jimu_config (
        id VARCHAR(50) PRIMARY KEY,
        http_id VARCHAR(50) NOT NULL UNIQUE,
        name NVARCHAR(100),
        url NVARCHAR(500) NOT NULL,
        method VARCHAR(10) DEFAULT 'POST',
        headers NVARCHAR(MAX), -- JSON string for headers
        query_params NVARCHAR(MAX), -- JSON string for URL query parameters
        body_config NVARCHAR(MAX), -- JSON string for initial body or raw content
        body_type VARCHAR(20) DEFAULT 'none', -- none, form-data, x-www-form-urlencoded, raw
        body_raw_type VARCHAR(20) DEFAULT 'json', -- text, javascript, json, html, xml
        cron_config NVARCHAR(500), -- Cron expression or visual config JSON
        enable_job BIT DEFAULT 0, -- 1: Enabled, 0: Disabled
        params_config NVARCHAR(MAX), -- Deprecated or used for mapping
        steps_config NVARCHAR(MAX), -- JSON string for the flow steps
        pool_id VARCHAR(50), -- Associated connection pool ID
        connect_timeout INT, -- Independent connection timeout (ms)
        read_timeout INT, -- Independent read timeout (ms)
        write_timeout INT, -- Independent write timeout (ms)
        call_timeout INT, -- overall call timeout milliseconds
        retry_on_connection_failure BIT, -- retry on connection failure
        follow_redirects BIT, -- follow HTTP redirects
        follow_ssl_redirects BIT, -- follow SSL redirects
        dns_overrides NVARCHAR(MAX), -- DNS override JSON
        proxy_host VARCHAR(255), -- proxy host
        proxy_port INT, -- proxy port
        proxy_type VARCHAR(20), -- proxy type: HTTP/SOCKS
        create_time DATETIME2 DEFAULT GETDATE(),
        update_time DATETIME2 DEFAULT GETDATE()
    );
END
GO

-- 2. Add missing columns to http_jimu_config if they don't exist
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'http_jimu_config')
BEGIN
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'query_params')
    BEGIN
        ALTER TABLE http_jimu_config ADD query_params NVARCHAR(MAX);
    END
    
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'body_config')
    BEGIN
        ALTER TABLE http_jimu_config ADD body_config NVARCHAR(MAX);
    END

    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'body_type')
    BEGIN
        ALTER TABLE http_jimu_config ADD body_type VARCHAR(20) DEFAULT 'none';
    END

    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'body_raw_type')
    BEGIN
        ALTER TABLE http_jimu_config ADD body_raw_type VARCHAR(20) DEFAULT 'json';
    END

    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'cron_config')
    BEGIN
        ALTER TABLE http_jimu_config ADD cron_config NVARCHAR(500);
    END

    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'enable_job')
    BEGIN
        ALTER TABLE http_jimu_config ADD enable_job BIT DEFAULT 0;
    END

    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'pool_id')
    BEGIN
        ALTER TABLE http_jimu_config ADD pool_id VARCHAR(50);
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'connect_timeout')
    BEGIN
        ALTER TABLE http_jimu_config ADD connect_timeout INT;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'read_timeout')
    BEGIN
        ALTER TABLE http_jimu_config ADD read_timeout INT;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'write_timeout')
    BEGIN
        ALTER TABLE http_jimu_config ADD write_timeout INT;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'call_timeout')
    BEGIN
        ALTER TABLE http_jimu_config ADD call_timeout INT;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'retry_on_connection_failure')
    BEGIN
        ALTER TABLE http_jimu_config ADD retry_on_connection_failure BIT;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'follow_redirects')
    BEGIN
        ALTER TABLE http_jimu_config ADD follow_redirects BIT;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'follow_ssl_redirects')
    BEGIN
        ALTER TABLE http_jimu_config ADD follow_ssl_redirects BIT;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'dns_overrides')
    BEGIN
        ALTER TABLE http_jimu_config ADD dns_overrides NVARCHAR(MAX);
    END

    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'proxy_host')
    BEGIN
        ALTER TABLE http_jimu_config ADD proxy_host VARCHAR(255);
    END

    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'proxy_port')
    BEGIN
        ALTER TABLE http_jimu_config ADD proxy_port INT;
    END

    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_config') AND name = 'proxy_type')
    BEGIN
        ALTER TABLE http_jimu_config ADD proxy_type VARCHAR(20);
    END
END
GO

-- 3. Create http_jimu_job_log if not exists
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'http_jimu_job_log')
BEGIN
    CREATE TABLE http_jimu_job_log (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        config_id VARCHAR(50) NOT NULL,
        http_id VARCHAR(50) NOT NULL,
        input_params NVARCHAR(MAX),
        output_result NVARCHAR(MAX),
        status VARCHAR(20), -- SUCCESS, ERROR
        error_msg NVARCHAR(MAX),
        duration BIGINT, -- milliseconds
        create_time DATETIME2 DEFAULT GETDATE()
    );
    CREATE INDEX idx_job_log_config ON http_jimu_job_log(config_id);
END
GO

-- 4. Create http_jimu_step if not exists
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'http_jimu_step')
BEGIN
    CREATE TABLE http_jimu_step (
        id VARCHAR(50) PRIMARY KEY,
        code VARCHAR(50) NOT NULL UNIQUE,
        name NVARCHAR(100) NOT NULL,
        type VARCHAR(20) NOT NULL, -- SORT, SIGN, ENCRYPT, ADD_FIXED, SCRIPT
        target VARCHAR(20) DEFAULT 'BODY', -- HEADER, BODY, QUERY, FORM
        script_content NVARCHAR(MAX), -- For SCRIPT type
        config_json NVARCHAR(MAX), -- Default configuration for the step
        input_schema NVARCHAR(MAX), -- Description or JSON schema of expected input
        output_schema NVARCHAR(MAX), -- Description or JSON schema of output
        description NVARCHAR(500),
        create_time DATETIME2 DEFAULT GETDATE(),
        update_time DATETIME2 DEFAULT GETDATE()
    );
END
GO

-- 5. Create http_jimu_pool if not exists
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'http_jimu_pool')
BEGIN
    CREATE TABLE http_jimu_pool (
        id VARCHAR(50) PRIMARY KEY,
        name NVARCHAR(100) NOT NULL UNIQUE,
        max_idle_connections INT DEFAULT 5,
        keep_alive_duration BIGINT DEFAULT 300000, -- milliseconds (5 minutes)
        connect_timeout INT DEFAULT 10000, -- milliseconds
        read_timeout INT DEFAULT 10000, -- milliseconds
        write_timeout INT DEFAULT 10000, -- milliseconds
        call_timeout INT DEFAULT 0, -- overall call timeout milliseconds
        retry_on_connection_failure BIT DEFAULT 1, -- retry on connection failure
        follow_redirects BIT DEFAULT 1, -- follow HTTP redirects
        follow_ssl_redirects BIT DEFAULT 1, -- follow SSL redirects
        max_requests INT DEFAULT 64, -- dispatcher max requests
        max_requests_per_host INT DEFAULT 5, -- dispatcher max requests per host
        ping_interval INT DEFAULT 0, -- HTTP/2 ping interval milliseconds
        dns_overrides NVARCHAR(MAX), -- DNS override JSON
        proxy_host VARCHAR(255), -- proxy host
        proxy_port INT, -- proxy port
        proxy_type VARCHAR(20), -- proxy type: HTTP/SOCKS
        create_time DATETIME2 DEFAULT GETDATE(),
        update_time DATETIME2 DEFAULT GETDATE()
    );
END
GO

IF EXISTS (SELECT * FROM sys.tables WHERE name = 'http_jimu_pool')
BEGIN
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_pool') AND name = 'call_timeout')
    BEGIN
        ALTER TABLE http_jimu_pool ADD call_timeout INT DEFAULT 0;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_pool') AND name = 'retry_on_connection_failure')
    BEGIN
        ALTER TABLE http_jimu_pool ADD retry_on_connection_failure BIT DEFAULT 1;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_pool') AND name = 'follow_redirects')
    BEGIN
        ALTER TABLE http_jimu_pool ADD follow_redirects BIT DEFAULT 1;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_pool') AND name = 'follow_ssl_redirects')
    BEGIN
        ALTER TABLE http_jimu_pool ADD follow_ssl_redirects BIT DEFAULT 1;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_pool') AND name = 'max_requests')
    BEGIN
        ALTER TABLE http_jimu_pool ADD max_requests INT DEFAULT 64;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_pool') AND name = 'max_requests_per_host')
    BEGIN
        ALTER TABLE http_jimu_pool ADD max_requests_per_host INT DEFAULT 5;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_pool') AND name = 'ping_interval')
    BEGIN
        ALTER TABLE http_jimu_pool ADD ping_interval INT DEFAULT 0;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_pool') AND name = 'dns_overrides')
    BEGIN
        ALTER TABLE http_jimu_pool ADD dns_overrides NVARCHAR(MAX);
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_pool') AND name = 'proxy_host')
    BEGIN
        ALTER TABLE http_jimu_pool ADD proxy_host VARCHAR(255);
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_pool') AND name = 'proxy_port')
    BEGIN
        ALTER TABLE http_jimu_pool ADD proxy_port INT;
    END
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('http_jimu_pool') AND name = 'proxy_type')
    BEGIN
        ALTER TABLE http_jimu_pool ADD proxy_type VARCHAR(20);
    END
END
GO




