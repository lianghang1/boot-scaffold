# boot-scaffold

内部 Spring Boot 脚手架（JDK 1.8 / Spring Boot 2.7.18）。框架仓库只产 jar，不含启动类；宿主由各项目仓库自行组装。

## 模块

```
boot-scaffold/
├── scaffold-parent           统一父 POM：版本 + 插件基线，外部项目继承它复用
├── scaffold-dependencies     BOM：全部依赖版本唯一出口
├── scaffold-common           公共工具（异常/JSON/日期/枚举等）
└── scaffold-starters/        能力积木，按需依赖，自动装配
    ├── starter-web           Web 基础：traceId / 全局异常 / 访问日志 / Jackson
    ├── starter-log           日志基线
    ├── starter-ip            IP 归属地解析
    ├── starter-data-jpa      数据访问：BaseEntity / 雪花 ID / 乐观锁 / 多数据源 @TargetDataSource
    ├── starter-liquibase     Liquibase 集成：国产库 Database 派生 / 多数据源按库迁移（可选）
    ├── starter-tenant        多租户：JPA @Filter / Bean Searcher / findById 兜底三通路（opt-in 可选）
    ├── starter-query         动态检索（Bean Searcher 封装 + doNativeQuery；复杂 SQL 优先内联 @SearchBean）
    ├── starter-security      认证鉴权：token 会话(Redis) / 三入口安全链 / openapi 签名
    ├── starter-cache         Caffeine L1 + Redis L2 两级缓存
    ├── starter-redis         Redis 客户端封装
    ├── starter-protection    限流 / 幂等 / 分布式锁
    ├── starter-job           AbstractJob 定时任务体系（执行日志 / 分布式锁）
    ├── starter-powerjob      PowerJob 执行器适配
    ├── starter-crypto        敏感字段加密（AES-256-GCM / SM4）
    ├── starter-http          出站 HTTP（okhttp 封装：重试 / traceId 透传）
    ├── starter-storage       文件存储（x-file-storage 封装）
    ├── starter-message       短信 + 邮件
    ├── starter-push          SSE 实时推送
    ├── starter-ws            WebSocket 双向通道
    ├── starter-observability Micrometer + Prometheus 埋点
    └── starter-test          统一测试底座
```

## 项目怎么用

项目仓库 pom 继承 parent，依赖零版本号：

```xml
<parent>
    <groupId>com.sfi.scaffold</groupId>
    <artifactId>scaffold-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
```

```xml
<dependency>
    <groupId>com.sfi.scaffold</groupId>
    <artifactId>scaffold-starter-web</artifactId>
</dependency>
```

业务代码包名用 `com.sfi.<项目名>.*`，与框架包隔离；启动类放包根，框架走自动装配不进扫描范围。

## 能力速查

### 数据库方言（国产化适配）

一处声明 `db-type`，三条 SQL 通路（JPA / Bean Searcher / doNativeQuery）自动派生方言，禁止各通路各配一种：

```yaml
# 单库：一行声明
scaffold:
  database:
    db-type: KINGBASE

# 多库：全局兜底 + 异构库内联（一个数据源一个块说清所有事）
scaffold:
  database:
    db-type: MYSQL
  datasource:
    dynamic:
      datasources:
        master:    { url: jdbc:mysql://... }
        cemetery:  { db-type: KINGBASE, url: jdbc:kingbase8://... }
        archive:   { db-type: DAMENG,   url: jdbc:dm://... }
```

- 解析链：数据源块内 `db-type` → 全局 `db-type` → MYSQL（不声明则三通路维持现状，零影响）
- 枚举 9 值：MYSQL / POSTGRESQL / ORACLE / SQLSERVER / DAMENG / KINGBASE / GAUSS / OCEANBASE / UNKNOWN
- 用户自定义 Dialect Bean、`spring.jpa.database-platform` 显式配置永远优先，脚手架退让
- 边界：不做 URL 自动推断（Boot 2.7 不认国产库，业界无先例）；非 MySQL 系计划类禁用 `useIndex`（启动期报错）；SQLServer 分页（FETCH 组）要求语句含 ORDER BY；OceanBase 按 MySQL 模式处理

### Liquibase

单库用官方 `spring.liquibase.*`（starter 零介入）；多库内联到数据源块，配了 `change-log` 的库才建 `SpringLiquibase`：

```yaml
scaffold:
  datasource:
    dynamic:
      datasources:
        cemetery:
          db-type: KINGBASE
          url: jdbc:kingbase8://...
          liquibase:
            change-log: classpath:db/cemetery.yaml
```

- 国产库自动借壳：金仓 / OpenGauss → PostgreSQL 方言，OceanBase → MySQL 方言；达梦探测社区 liquibase-dm（缺失 WARN 跳过，不硬绑社区包）
- 与 `spring.jpa.hibernate.ddl-auto` 并存时 WARN：schema 管理权归 Liquibase，建议显式关闭
- liquibase-core 随 starter 传递（无需单独引入）；未配 change-log 的库零影响

### 多租户（opt-in）

字段隔离：业务表含 `tenant_id` 列。`scaffold.tenant.enabled: true` 后三条通路自动过滤；租户来源 = 登录身份的 `tenantId`（登录时业务写入 `SessionInfo`，null = 平台身份全放行）：

| 位置 | 写什么 |
|---|---|
| 实体 | `@Filter(name = JpaTenantFilterAspect.FILTER_NAME, condition = "tenant_id = :tenantId")`，白名单式一行声明 |
| 计划类 | `tenantId` 字段（有则自动注入等值条件，前端传参被固定值覆盖不可篡改） |
| job / MQ | `TenantScope.execute(tenantId, runnable)`（try/finally 恢复现场，防线程池泄漏串号） |
| 跨租户场景 | `@TenantIgnore`（方法 / 计划类）或 `scaffold.tenant.ignore-tables` 配置 |

边界（必须知道）：

- `QueryUtilsX.doNativeQuery(sql)` / 直连 JdbcTemplate **不注入**租户条件，业务自含
- BS 内联子查询（`@SearchBean(tables = "(SELECT ...) t")`）**子查询内部**不过滤
- findById 跨租户**返回空 + WARN**（防存在性探测，不报 403）
- 设计取舍：接入声明与 `@Entity` / `@ColumnX` 同级心智成本，不做注册表 / 启动校验 / 强制卡点；Hibernate 5.6 不支持 `@Filter` 组合注解，必须直写

## 构建 / 发布

```bash
mvn clean test      # 全量测试
mvn deploy \
    -Dnexus.releases=http://<nexus>/repository/maven-releases/ \
    -Dnexus.snapshots=http://<nexus>/repository/maven-snapshots/
```

