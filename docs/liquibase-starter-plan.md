# Liquibase Starter · 方案计划

> 状态：**已实施**（scaffold-starter-liquibase，模块测试全绿、全仓库编译通过）。
> 依赖前置：`DatabaseType` 统一枚举（已实施，见 [database-dialect-plan.md](database-dialect-plan.md)）。

## 0. 实施中的 API 事实修正（以项目实际依赖 liquibase-core 4.9.1 为准）

1. Database 匹配入口是 `isCorrectDatabaseImplementation(DatabaseConnection)`（按 JDBC 元数据 DatabaseProductName 判断）+ `getDefaultDriver(url)`，**不是**设计时推测的 `holdsUrl`
2. 匹配全部未命中**不抛异常**，Liquibase fallback 到 `UnsupportedDatabase`（LOG.warning）——因此达梦 WARN 提前提示更有价值
3. `register()` 按 `getShortName()` 分组、组内按 priority 排序取组首 → 自定义类重写 `getShortName()`（kingbase8/opengauss/oceanbase）独立分组，不与官方 postgresql/mysql 组竞争
4. 4.9.x 类名：`liquibase.database.core.PostgresDatabase`（非 PostgreSQLDatabase）、基类 `AbstractJdbcDatabase`；`getDefaultDatabaseProductName()` 是 **protected**，接口层拿不到 → 达梦检查无法预演，**降级为声明 DAMENG 即无条件 WARN**
5. `AbstractRoutingDataSource.getResolvedDataSources()` 返回 `Map<Object, DataSource>`
6. `ConfigurationCondition` 在 `org.springframework.context.annotation`（framework），`matches` 为两参签名

## 1. 背景与目标

**生态现状**（已核实官方文档与社区）：

- Liquibase 按 JDBC URL 前缀探测数据库，内置不认识 `jdbc:kingbase8:` / `jdbc:dm:` / `jdbc:opengauss:` → **直接抛 DatabaseException**
- 业界三条路径：① 借壳主流方言（金仓官方移植文档自己建议框架配 postgresql 方言）；② 社区适配扩展（金仓 GitHub 三模式适配分支、达梦官方指导自建 DMDatabase / 社区 liquibase-dm）；③ 放弃应用层迁移（DTS 搬库 + 手工 DDL）
- 达梦社区适配存在踩坑记录（如二次启动 DATABASECHANGELOG 冲突），质量参差 → 不可硬绑任何社区包

**目标**：`scaffold-starter-liquibase` 可选 starter，封装路径 ①②：

1. 国产库通过 `DatabaseType` 声明自动接入 Liquibase（借壳 PG / 探测社区扩展）
2. 多数据源按数据源块内联配置各自管理 schema
3. 未引依赖 / 未配置 → 零影响（官方自动配置照常）

**非目标**：不内置达梦/金仓完整 Database 实现（那是社区扩展的活）；不做 changelog 生成工具。

## 2. 配置面（内联定稿）

**单库**：官方 `spring.liquibase.*`，不重复造，starter 完全不介入。

**多库**：内联到数据源块（与 db-type 同构，一个数据源一个块说清一切）：

```yaml
scaffold:
  datasource:
    dynamic:
      datasources:
        master:    { db-type: MYSQL,    url: jdbc:mysql://... }
        cemetery:
          db-type: KINGBASE
          url: jdbc:kingbase8://...
          liquibase:                          # 归 liquibase starter 消费，data-jpa 零感知
            change-log: classpath:db/cemetery.yaml
        archive:
          db-type: DAMENG
          url: jdbc:dm://...
          liquibase:
            change-log: classpath:db/archive.yaml
```

**解耦机制**（本次讨论的关键决策）：

- data-jpa 的 `DynamicDataSourceProperties.Conf` **不加字段**（`@ConfigurationProperties` 未知字段默认忽略）
- liquibase starter 用 **Binder 直读** `scaffold.datasource.dynamic.datasources.<name>.liquibase.*`
- 配置内聚、模块解耦两全；metadata 提示注明归属
- 配了 `liquibase.change-log` 的数据源才创建对应 `SpringLiquibase`；未配的库 schema 不归本系统管，不动

## 3. 设计总览

### 3.1 模块与依赖

```
scaffold-starters/scaffold-starter-liquibase/
  依赖：liquibase-core（compile，Boot 2.7 BOM 管理 4.17.x，JDK8 兼容）
       spring-jdbc、spring-boot-starter（已有生态）
  可选探测：liquibase-dm（达梦社区扩展，classpath 探测，不进 BOM）
  不依赖：data-jpa、bean-searcher
```

### 3.2 DatabaseType → Liquibase Database 派生（核心）

| DatabaseType | Liquibase 侧处理 |
|---|---|
| MYSQL / POSTGRESQL / ORACLE / SQLSERVER | 官方 Database，零干预（URL 自识别即可） |
| OCEANBASE | 自定义 `OceanBaseDatabase extends MySQLDatabase`，URL 匹配接受 `jdbc:oceanbase:` |
| KINGBASE | 自定义 `KingbaseDatabase extends PostgreSQLDatabase`，接受 `jdbc:kingbase8:`（PG 方言直接复用） |
| GAUSS | 自定义 `OpenGaussDatabase extends PostgreSQLDatabase`，接受 `jdbc:opengauss:` |
| DAMENG | classpath 探测社区扩展 `liquibase-dm`（有 → 注册其 Database；缺 → WARN 跳过，不阻塞启动） |

**注册机制**：仅在声明了对应 `db-type` 时向 `DatabaseFactory` 注册自定义 Database（静态注册但按需激活，且自定义实现的 URL 匹配只认国产库前缀，对主流库零干扰）。4.x 的匹配入口是 `DatabaseFactory` 的 URL 探测链（实施时以 4.17 实际 API 为准，`holdsUrl`/priority）。

### 3.3 多库 SpringLiquibase 组装

- 数据源池获取：`ObjectProvider<AbstractRoutingDataSource>`（spring-jdbc 类，**不依赖 data-jpa 实现**）
  → `getResolvedDataSources()` 拿 `Map<数据源名, DataSource>`（Bean Searcher 多数据源已验证同款路径）
- 每个内联了 `liquibase.change-log` 的数据源 → 组装一个 `SpringLiquibase` Bean（`dataSource = resolved.get(name)`）
- 官方 `LiquibaseAutoConfiguration` 带 `@ConditionalOnMissingBean(SpringLiquibase.class)` →
  我们组装时官方退让，**单库官方 / 多库脚手架**互不越界
- 执行顺序：`dependsOn` dynamic 数据源 Bean，路由初始化完成后才跑迁移

### 3.4 优先级与冲突防护

| 情形 | 行为 |
|---|---|
| 未引 liquibase-core | starter 整体不激活（`@ConditionalOnClass`） |
| 单库 + spring.liquibase 配置 | 官方自动配置接管，starter 不创建任何 Bean |
| 多库 + 数据源块内联 change-log | starter 按库组装，官方退让 |
| 宿主自定义 SpringLiquibase Bean | starter 退让（`@ConditionalOnMissingBean`） |
| `spring.jpa.hibernate.ddl-auto` 非 none 且 starter 激活 | WARN：schema 管理权归 Liquibase，建议显式关闭 |
| 数据源块声明 db-type 但未配 change-log | 该库不做迁移（只派生方言，与 Liquibase 无关） |

## 4. 分步实施清单

### Step 1：starter 骨架 + Database 派生（~150 行）
- [ ] `scaffold-starter-liquibase` 模块（pom、imports 注册）
- [ ] `KingbaseDatabase` / `OpenGaussDatabase` / `OceanBaseDatabase`（extends 官方，仅重写 URL 匹配）
- [ ] `ScaffoldDatabaseRegistrar`：按 Environment 声明按需注册进 `DatabaseFactory`；DAMENG classpath 探测 liquibase-dm，缺失 WARN

### Step 2：多库组装（~100 行）
- [ ] `ScaffoldLiquibaseConfiguration`：Binder 读块内 `liquibase.change-log` + `AbstractRoutingDataSource` resolved map → 多个 `SpringLiquibase` Bean（BeanFactoryPostProcessor 动态注册，数量随配置）
- [ ] ddl-auto 冲突 WARN；单库/宿主 Bean 退让条件

### Step 3：测试（~150 行）
- [ ] 三个自定义 Database 的 URL 匹配单测（kingbase8/opengauss/oceanbase 接受，主流前缀不抢占）
- [ ] Registrar 激活条件（声明才注册、未声明不注册）
- [ ] ApplicationContextRunner：内联配置 → 每库一个 SpringLiquibase 且 dataSource 归属正确；未配 → 零 Bean；宿主 Bean → 退让
- [ ] 全仓库回归

### Step 4：文档
- [ ] metadata：`datasources.<name>.liquibase.*` 归属提示
- [ ] README 集成说明（含"复杂 SQL 内联 @SearchBean"原则与本文档互链）

## 5. 边界与明确不做

1. **不内置达梦完整 Database 实现**——社区 `liquibase-dm` 探测启用，缺失 WARN（达梦社区适配踩坑记录多，质量参差，硬绑会把坑带进脚手架）
2. **金仓借壳 PG 的边角差异**（自有类型/保留字）是 Liquibase 生态现状，starter 保证"能用 + 声明清晰"，不承诺零差异
3. **不做** changelog 代码生成、diff、rollback UI——liquibase-maven-plugin / CLI 已有
4. **单库场景零发明**——官方 `spring.liquibase.*` 原样可用
5. Liquibase 版本随 Boot 2.7 BOM 解析（本项目实际 4.9.1），不显式锁版本、不随大版本漂移
6. `AbstractRoutingDataSource` Bean 不存在（多库未启用）但配了块内 change-log → 启动 fail-fast 报"数据源未启用，无法执行迁移"

## 6. 工作量（实际）

| 项 | 估算 | 实际 |
|---|---|---|
| starter 主代码 | ~250 行 | 6 类 ~430 行（含 Javadoc） |
| 测试 | ~150 行 | 3 类 14 用例 |
| 文档/metadata | ~40 行 | metadata 1 项 + README 2 行 + 本文档 |

> 风险点已消解：Step 1 首个任务即核实了 4.9.1 真实 API（见第 0 节），注册方式按源码事实落地，未影响整体设计。
> 达梦 WARN 语义微调：由"探测缺失才 WARN"降级为"声明 DAMENG 即 WARN"（探测依赖 protected API，无可靠启动期探测手段）。
