# HTTP Jimu

HTTP Jimu 是一个用于可视化编排第三方 HTTP 调用的 Spring Boot Starter。

它同时提供：

- 页面端：通过 `/http_jimu.html` 配置 HTTP 接口、步骤库、连接池、定时任务和执行日志
- 服务端：自动注册 `/http-jimu-api/**` 管理与测试接口
- 代码端：通过 `httpId` 在业务代码中直接发起调用

适合把签名、加密、参数整理、脚本处理、响应修整这类“变化频繁但结构稳定”的外部接口逻辑从业务代码中抽离出来。

---

## 核心能力

### 1. 可视化 HTTP 编排

页面地址：

- `/http_jimu.html`

支持能力：

- HTTP 配置管理：增删改查
- Postman 风格请求配置：`method`、`url`、`queryParams`、`headers`、`body`
- Body 类型：`raw`、`form-data`、`x-www-form-urlencoded`、`none`
- Raw Body 媒体类型：`text`、`javascript`、`json`、`html`、`xml`
- 请求预览：只执行参数准备和步骤链，不发真实外部请求
- 测试调用：发真实外部请求并返回完整请求/响应细节
- 定时任务配置与执行日志
- 连接池与超时、代理、DNS、并发参数管理

### 2. 步骤链引擎

内置步骤类型：

- `SORT`
- `SIGN`
- `ENCRYPT`
- `ADD_FIXED`
- `SCRIPT`

步骤目标：

- 请求阶段：`HEADER`、`BODY`、`QUERY`、`FORM`
- 响应阶段：`RESPONSE_BODY`、`RESPONSE_HEADER`、`RESPONSE_STATUS`

说明：

- 请求步骤会在真正发请求前执行
- 响应步骤会在拿到响应后继续加工结果
- `preview` 只跑请求阶段步骤
- `test-call` 会返回 `stepTraces`

### 3. 占位符解析

支持占位符：

- `${key}`：读取调用入参
- `${env:xxx}`：读取 Spring `Environment`
- `${redis:xxx}`：读取 Redis

占位符校验会在保存配置时执行，能提前挡住：

- 未闭合的 `${...}`
- 空占位符 `${}`
- 不受支持的表达式格式

JSON Body 的占位符替换按叶子字符串递归处理，避免直接对整段 JSON 做字符串替换导致结构损坏。

### 4. 脚本扩展（Groovy）

`SCRIPT` 步骤可用变量：

- `body`
- `context`
- `url`
- `headers`
- `queryParams`
- `log`
- `redis`
- `bean`
- `beans`

说明：

- `bean("orderService")` 可按名称取 Spring Bean
- `beans.xxxService` 可按名称访问 Bean
- 脚本类会做进程内缓存，避免重复编译
- 脚本校验接口只做语法/编译校验，不执行实际逻辑

### 5. 重试与传输控制

当前支持两层重试能力：

- OkHttp 连接级重试：`retryOnConnectionFailure`
- 业务级 HTTP 状态码重试：`retryMaxAttempts` + `retryOnHttpStatus`

其中业务级重试规则：

- `retryMaxAttempts = 0` 或空：不做状态码重试
- `retryOnHttpStatus` 形如 `502,503,504`
- 命中配置状态码时，会在相同 prepared request 上重试

### 6. 连接池、代理、DNS 与超时

连接池可配置：

- `maxIdleConnections`
- `keepAliveDuration`
- `connectTimeout`
- `readTimeout`
- `writeTimeout`
- `callTimeout`
- `retryOnConnectionFailure`
- `followRedirects`
- `followSslRedirects`
- `maxRequests`
- `maxRequestsPerHost`
- `pingInterval`
- `dnsOverrides`
- `proxyHost`
- `proxyPort`
- `proxyType`

说明：

- 可通过 `poolId` 绑定公共连接池
- 也可以在单个 `HttpJimuConfig` 上覆盖超时、DNS、代理等参数
- 代理支持 `HTTP` 和 `SOCKS`
- DNS 覆盖格式：`{"api.example.com":"1.1.1.1"}`

### 7. 响应明细与大响应保护

`test-call` / `callWithDetail` 返回：

- 请求方法、URL、Header、Body
- 响应状态码、Header、Body
- 执行耗时
- `stepTraces`

响应体处理规则：

- 文本按 `Content-Type` 推断字符集，默认 UTF-8
- 常见二进制响应会转 Base64
- 单次响应体读取上限为 5MB，超出会返回提示文本，避免 OOM

### 8. 缓存与元数据

项目统一通过 `JimuCacheProvider` 做业务缓存。

自动装配规则：

- 存在 `StringRedisTemplate`：默认使用 Redis 缓存
- 否则：默认使用内存缓存

当前缓存场景：

- `httpId -> HttpJimuConfig`
- `stepsConfig` 解析结果
- `scriptMeta`
- `beanMeta`

说明：

- 内存缓存使用 Caffeine，并保留每条缓存自己的 TTL 语义
- 连接池对象、Groovy 编译类缓存和调度运行标记属于进程内资源，不走 Redis

### 9. 定时任务与分布式锁

配置项：

- `enableJob`
- `cronConfig`

启动时会自动扫描已启用任务并注册调度。

锁策略按顺序自动降级：

1. Redis 锁
2. JDBC ShedLock
3. 单机内存防重入

说明：

- 有 Redis 时，使用 `SETNX + TTL + Lua` 释放锁
- 无 Redis 但有 `DataSource` 且存在 ShedLock 依赖时，自动启用 JDBC 锁
- 两者都没有时，仅保证单 JVM 内不重入
- 执行日志异步写库，使用受控有界线程池，避免阻塞调度线程

---

## 快速接入

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.jimu</groupId>
    <artifactId>http-jimu-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

业务应用还需要自行引入数据库驱动，例如：

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

### 2. 准备数据表

当前运行依赖以下表：

- `http_jimu_config`
- `http_jimu_pool`
- `http_jimu_step`
- `http_jimu_job_log`
- `jimu_shedlock`（仅使用 JDBC 分布式锁时需要）

可参考：

- `testdemo/src/main/resources/schema.sql`

注意：

- 当前代码实体里 `http_jimu_config` 已包含 `retry_max_attempts`、`retry_on_http_status` 两个字段语义
- 如果你的历史表结构还没有这两个字段，需要自行补齐对应列

### 3. 基础配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456

jimu:
  cache:
    http-id-ttl-ms: 3600000
    steps-ttl-ms: 1800000
    script-meta-ttl-ms: 3600000
    bean-meta-ttl-ms: 600000
  scheduler:
    default-lock-ttl-seconds: 120
    min-lock-ttl-seconds: 30
    pool-size: 5
  script:
    cache-max: 512
```

访问页面：

- `http://localhost:8080/http_jimu.html`

---

## 代码调用

### 1. 直接取响应体

```java
@Service
public class DemoService {

    @Autowired
    private HttpJimuService httpJimuService;

    public String callRemote() {
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", "12345");
        return httpJimuService.call("my_api_id", params);
    }
}
```

### 2. 获取完整明细

```java
Map<String, Object> params = Map.of("orderId", "12345");
ExecuteDetail detail = httpJimuService.callWithDetail("my_api_id", params);
```

### 3. 只预览请求构造结果

```java
PreviewDetail preview = httpJimuService.preview("my_api_id", Map.of("orderId", "12345"));
```

---

## 页面接口

基础配置：

- `GET /http-jimu-api/list`
- `POST /http-jimu-api/save`
- `DELETE /http-jimu-api/delete/{id}`
- `POST /http-jimu-api/preview-call/{httpId}`
- `POST /http-jimu-api/test-call/{httpId}`

连接池：

- `GET /http-jimu-api/pools`
- `POST /http-jimu-api/pools/save`
- `DELETE /http-jimu-api/pools/delete/{id}`

步骤库：

- `GET /http-jimu-api/steps`
- `POST /http-jimu-api/steps/save`
- `DELETE /http-jimu-api/steps/delete/{id}`

脚本元数据：

- `GET /http-jimu-api/script-meta`
- `GET /http-jimu-api/script-meta/bean/{beanName}`
- `POST /http-jimu-api/script-meta/cache/evict`
- `POST /http-jimu-api/validate-script`

任务日志：

- `GET /http-jimu-api/job-logs/{configId}`

---

## 关键对象说明

### `HttpJimuConfig`

核心字段：

- `httpId`：业务调用 ID，代码里按它查配置
- `url`、`method`
- `headers`、`queryParams`、`bodyConfig`
- `bodyType`、`bodyRawType`
- `stepsConfig`
- `poolId`
- `connectTimeout`、`readTimeout`、`writeTimeout`、`callTimeout`
- `retryOnConnectionFailure`
- `followRedirects`、`followSslRedirects`
- `dnsOverrides`
- `proxyHost`、`proxyPort`、`proxyType`
- `retryMaxAttempts`
- `retryOnHttpStatus`
- `enableJob`、`cronConfig`

### `HttpJimuStep`

步骤库字段：

- `code`
- `name`
- `type`
- `target`
- `scriptContent`
- `configJson`
- `inputSchema`
- `outputSchema`
- `description`

### `HttpJimuPool`

连接池模板字段：

- 空闲连接、保活、超时
- Dispatcher 并发参数
- 重定向、连接失败重试
- DNS override
- 代理配置

---

## 项目模块

- `http-jimu-spring-boot-starter`：Starter 与核心实现
- `testdemo`：演示项目与示例建表脚本

---

## 运行要求

- JDK 21+
- Spring Boot 3.x
- MyBatis-Plus
- 数据库
- Redis 可选

构建已通过 Maven Enforcer 强制要求 JDK 21+。

---

## 注意事项

- Starter 自动导入了 Controller、Engine、Scheduler、StepProcessor、Service 等 Bean
- 若应用未引入 Web 环境，页面与 `/http-jimu-api/**` 自然不会作为 HTTP 服务对外提供，但代码调用能力仍可复用
- `SCRIPT` 步骤允许访问 Spring Bean，生产环境建议对页面与接口做鉴权，并限制可暴露的 Bean 范围
- 大响应体会被 5MB 保护截断，不适合拿来处理真正的流式下载场景

---

## 安全建议

- 为 `/http-jimu-api/**` 增加登录态与权限控制
- 对 `bean("...")` / `beans.xxx` 能访问的 Bean 做白名单约束
- 不要把高敏感密钥直接写死在脚本中，优先放到环境变量或外部密钥系统
