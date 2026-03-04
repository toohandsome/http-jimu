# HTTP Jimu

HTTP Jimu 是一个用于 **可视化编排第三方 HTTP 调用** 的 Spring Boot 组件。

它不提供 HTTP 服务端接口，而是让你通过页面像搭积木一样配置调用流程（参数处理、签名、加密、脚本处理、响应处理），然后在代码中通过 `httpId` 直接调用。

---

## 核心特性

### 1. 可视化编排页面

访问：`/http_jimu.html`

支持：

- 接口配置管理（增删改查）
- Postman 风格请求配置：Method / URL / Params / Headers / Body
- Body 类型：`raw`、`form-data`、`x-www-form-urlencoded`、`none`
- 预览调用与测试调用
- 定时任务配置与执行日志
- 连接池管理（超时、连接复用参数、重试/重定向/并发/心跳参数）

### 2. 积木步骤（流程引擎）

内置步骤类型：

- `SORT`：字段排序
- `SIGN`：签名
- `ENCRYPT`：加密/签名编排
- `ADD_FIXED`：注入固定参数
- `SCRIPT`：自定义脚本处理

作用目标：

- 请求阶段：`BODY` / `HEADER` / `QUERY` / `FORM`
- 响应阶段：`RESPONSE_BODY` / `RESPONSE_HEADER` / `RESPONSE_STATUS`

### 3. 占位符能力

支持占位符：

- `${key}`：从调用入参读取
- `${env:xxx}`：从 Spring Environment 读取
- `${redis:xxx}`：从 Redis 读取

保存配置时会进行占位符合法性校验。

### 4. 脚本能力（Groovy）

脚本可用变量：

- `body`
- `context`
- `url`
- `headers`
- `queryParams`
- `log`
- `redis`
- `bean`（`bean("orderService")` 获取 Spring Bean）
- `beans`（按名称访问 Bean）

支持 fastjson2 常用提示与调用：

- `JSON.parseObject(...)`
- `JSON.parseArray(...)`
- `JSON.toJSONString(...)`
- `JSONObject.of(...)`
- `JSONArray.of(...)`

### 5. 智能提示（Monaco + 元数据）

- Monaco 编辑器内置脚本提示
- 支持动态字段提示（来自当前参数/Header/Body 配置）
- 支持 Bean 名与 Bean 方法动态提示

### 6. 缓存策略（自动识别 Redis / 内存）

项目内缓存已统一抽象为 `JimuCacheProvider`：

- 有 `StringRedisTemplate`：自动启用 Redis 缓存实现
- 无 `StringRedisTemplate`：自动回退内存缓存实现

当前走统一缓存抽象的场景：

- `httpId -> HttpJimuConfig` 配置缓存
- `stepsConfig` 解析缓存
- 脚本元数据 `scriptMeta` / `beanMeta` 缓存

说明：`OkHttpClient` 连接池、脚本编译 Class 缓存、调度执行标记属于进程内对象，不走 Redis。

### 7. 多数据库适配（自动识别方言）

分页插件基于 JDBC `DatabaseMetaData` 自动识别数据库类型并选择方言，已去除 Starter 对 SQL Server 驱动的强依赖。

当前内置识别：

- SQL Server
- MySQL
- MariaDB
- PostgreSQL
- Oracle
- H2
- SQLite
- DB2

未识别到方言时会回退到 MyBatis-Plus 默认分页拦截器行为（仍可运行）。

### 8. OkHttp 连接池高级参数

连接池除基础参数外，已支持：

- `callTimeout`：整次调用总超时（ms）
- `retryOnConnectionFailure`：连接失败是否自动重试
- `followRedirects`：是否跟随 HTTP 重定向
- `followSslRedirects`：是否跟随 HTTPS 重定向
- `maxRequests`：全局最大并发请求数
- `maxRequestsPerHost`：单主机最大并发请求数
- `pingInterval`：HTTP/2 心跳间隔（ms）

---

## 代码调用

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

如需完整请求/响应细节：

```java
ExecuteDetail detail = httpJimuService.callWithDetail("my_api_id", params);
```

---

## 主要接口（页面侧）

- `GET /http-jimu-api/list`：接口配置列表
- `POST /http-jimu-api/save`：保存接口配置
- `POST /http-jimu-api/preview-call/{httpId}`：预览执行（不发真实外部请求）
- `POST /http-jimu-api/test-call/{httpId}`：测试执行（发真实外部请求）

脚本元数据：

- `GET /http-jimu-api/script-meta`
- `GET /http-jimu-api/script-meta/bean/{beanName}`
- `POST /http-jimu-api/script-meta/cache/evict`

脚本校验：

- `POST /http-jimu-api/validate-script`：仅做 Groovy **语法/编译校验**（`parse`），不会执行脚本逻辑

## 项目模块

- `http-jimu-spring-boot-starter`：核心功能模块
- `testdemo`：示例应用

---

## 运行要求

- Java 21
- Spring Boot 3.x
- JDBC 数据库（按需选择，如 MySQL / PostgreSQL / SQL Server / Oracle）
- Redis（可选）

本地 JDK 示例路径：

- `D:/Java/msjdk-21`

构建已启用 Maven Enforcer，要求 JDK 21+。

---

## Redis 可选接入说明

无需额外开关，项目自动识别是否存在 `StringRedisTemplate`：

- 配置了 Redis（且容器中有 `StringRedisTemplate` Bean）时，缓存自动走 Redis
- 未配置 Redis 时，缓存自动走内存

占位符 `${redis:key}` 的行为：

- 配置 Redis：读取真实 Redis 值
- 未配置 Redis：占位符解析为空字符串，并记录 warn 日志

---

## 多数据库接入说明

`http-jimu-spring-boot-starter` 不再内置具体数据库驱动。业务应用需要自行引入目标数据库 JDBC 依赖。

示例（任选其一）：

```xml
<!-- MySQL -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>

<!-- PostgreSQL -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>

<!-- SQL Server -->
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
</dependency>
```

然后按对应数据库配置 `spring.datasource.url/username/password/driver-class-name` 即可。

说明：`testdemo` 模块当前仍使用 SQL Server 作为默认演示配置，你可以替换成自己的数据库连接参数与驱动依赖。

---

## 定时任务分布式锁（自动降级）

当前定时任务执行顺序为：

1. Redis 锁（优先）
2. JDBC ShedLock（无 Redis 时）
3. 单机模式（Redis/JDBC 都不可用时）

说明：

- 多实例且启用 Redis：通过 `SETNX + TTL + Lua` 防止重复执行。
- 多实例无 Redis 但有 JDBC ShedLock：通过数据库行锁租约防止重复执行。
- 两者都不可用：仅保留进程内防重入（`AtomicBoolean`），跨实例会重复执行。

### ShedLock 表初始化（必须）

当你希望启用 JDBC ShedLock 时，请先创建表 `jimu_shedlock`。

SQL Server:

```sql
CREATE TABLE jimu_shedlock (
  name NVARCHAR(64) NOT NULL PRIMARY KEY,
  lock_until DATETIME2 NOT NULL,
  locked_at DATETIME2 NOT NULL,
  locked_by NVARCHAR(255) NOT NULL
);
```

MySQL:

```sql
CREATE TABLE jimu_shedlock (
  name VARCHAR(64) NOT NULL PRIMARY KEY,
  lock_until TIMESTAMP(3) NOT NULL,
  locked_at TIMESTAMP(3) NOT NULL,
  locked_by VARCHAR(255) NOT NULL
);
```

PostgreSQL:

```sql
CREATE TABLE jimu_shedlock (
  name VARCHAR(64) NOT NULL PRIMARY KEY,
  lock_until TIMESTAMP NOT NULL,
  locked_at TIMESTAMP NOT NULL,
  locked_by VARCHAR(255) NOT NULL
);
```

---

## 安全建议

当前脚本支持 `bean("name")` 访问 Spring Bean。

建议在生产环境增加 Bean 白名单控制，仅开放必要业务 Bean；并为 `/http-jimu-api/**` 增加鉴权与权限控制。
