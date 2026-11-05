# 国产化数据库方言统一 · 开发计划（已实施）

> 状态：**已实施并通过全仓库回归**（26 模块 BUILD SUCCESS，新增测试 24 个）。
> 命名决策：配置键采用 `db-type`（对齐 MyBatis-Plus `DbType` 先例，业界最广为人知），
> 否决初稿的 `product(s)`（语义偏"产品"，单复数混用别扭）。

## 1. 背景与目标

脚手架三条 SQL 通路各自带方言假设：

| 通路 | 方言现状 |
|---|---|
| `QueryUtilsX.doNativeQuery` | 手拼 `LIMIT :__limit OFFSET :__offset`（MySQL 系） |
| Bean Searcher（@TableX 计划 / @SearchBean） | BS 默认 MySQL 方言（`bean-searcher.sql.dialect` 可配） |
| Spring Data JPA | Hibernate 自识别 |

国产化数据库（金仓 KingbaseES、达梦 DM8、openGauss、OceanBase）JDBC URL 无法可靠推断
（Spring Boot 2.7.18 `DatabaseDriver` 已核实不含国产库），MyBatis-Plus / PageHelper /
Hibernate / Bean Searcher 业界全部要求显式声明，无零声明先例。

**目标：一处声明、各处消费。** 用户声明一次数据库类型，三条通路全部自动派生，禁止"各通路各配一种方言"。

## 2. Bean Searcher 覆盖可行性（已核实源码）

1. `BeanSearcherAutoConfiguration#dialect` 带 `@ConditionalOnMissingBean(Dialect.class)`——
   注册一个 Dialect Bean 即整体覆盖 `bean-searcher.sql.dialect`，`FieldOpPool`/`SqlResolver`/
   `DialectSqlInterceptor` 全链路自动换用，无需碰 SqlExecutor。
2. 动态方言无需自研路由：`DynamicDialect` 内部 static ThreadLocal 感知数据源；
   `DynamicDialectSupport`（ParamFilter + SqlInterceptor）从 `BeanMeta.getDataSource()`
   在检索前 `setCurrent(dsName)`、后清理——**自组装 DynamicDialect 必须同步注册它**
   （BS 仅在 `dialect-dynamic=true` 时注册，脚手架不依赖该配置项自行注册）。
3. 优先级靠自动配置顺序保证：桥接配置类 `before = BeanSearcherAutoConfiguration`
   先评估 `@ConditionalOnMissingBean`，从而 用户 Bean > 脚手架派生 > BS 默认。

## 3. 设计总览

### 3.1 配置面（类型跟着数据源块走，内联）

```yaml
scaffold:
  database:
    db-type: MYSQL            # 全局默认（单库场景的唯一声明处，多库作兜底）
  datasource:
    dynamic:
      primary: master
      datasources:
        master:
          db-type: MYSQL      # 块内联（可省略，回落全局）
          url: ...
        cemetery:
          db-type: KINGBASE   # 一个数据源一个块说清所有事
          url: ...
```

**解析链（单库/多库一致）：**

```
块内 db-type（scaffold.datasource.dynamic.datasources.<name>.db-type）
  → 全局 db-type（scaffold.database.db-type）
  → MYSQL（未声明维持现状）
```

- 单库走 `spring.datasource`（Spring 官方配置类无法内联），全局 `db-type` 即唯一声明处
- 多库内联声明后 `db-types` Map 概念整个消失，无两处 key 对齐问题
- 未声明任何 db-type → 三条通路全部维持现状，零影响

### 3.2 优先级矩阵

| 条件 | 行为 |
|---|---|
| 未声明 db-type | 三通路维持现状（BS 默认 / LIMIT 分页 / Hibernate 自识别） |
| 声明了 db-type | 脚手架派生，**覆盖** `bean-searcher.sql.dialect`（若冲突启动 WARN） |
| 用户自定义 Dialect Bean | 脚手架与 BS 全部退让 |
| `spring.jpa.database-platform` 显式配置 | 永远优先，脚手架不注入 |

### 3.3 DatabaseType 枚举（common，三框架映射集中）

`com.sfi.scaffold.common.database.DatabaseType`（common 零依赖，只出枚举 + 映射）：

- 9 值：MYSQL / POSTGRESQL / ORACLE / SQLSERVER / DAMENG / KINGBASE / GAUSS / OCEANBASE / UNKNOWN
- `paginationGroup()` → LIMIT 组（MySQL/PG/金仓/openGauss/OceanBase）或
  FETCH 组（Oracle/SQLServer/达梦，`OFFSET n ROWS FETCH NEXT m ROWS ONLY`）
- `isMySqlFamily()` → useIndex（FORCE INDEX）可用性判定
- `hibernatePlatform()` → KINGBASE/GAUSS→PostgreSQL10Dialect、OCEANBASE→MySQL57Dialect、
  DAMENG→社区 DmDialect（classpath 探测，缺失 WARN 跳过）；主流四库返回 null（自识别）

消费点各自组装方言实例（BS Dialect 桥接在 query 模块 switch，JPA 派生在 data-jpa），common 不引框架依赖。

## 4. 实施清单（全部完成）

### Step 1：scaffold-common
- [x] `DatabaseType` 枚举 + 单测（5 用例）

### Step 2：starter-query — doNativeQuery 分页方言化
- [x] `config/PaginationDialect`：LIMIT/FETCH 两组片段（命名参数 `:__limit`/`:__offset` 不变）
- [x] `config/DatabaseTypes`：Environment 收集声明 + 运行期 `resolve(dsName)` 解析链 + `DeclaredCondition`
- [x] `QueryUtilsX.doNativeQuery0` 接入（native 通路针对默认库，`resolve(null)`）
- [x] `QueryUtilsInitializer` 构造期 `DatabaseTypes.configure(environment)` 灌入

### Step 3：starter-query — BS Dialect 桥接 + useIndex 校验
- [x] `config/ScaffoldDialectConfiguration`（`before = BeanSearcherAutoConfiguration`）：
  - 单库 → 直接方言；多库 → `DynamicDialect`（default=primary 类型，具名数据源 put，
    声明集 = dynamic 块 keySet + 宿主 `NamedDataSource` Bean 名）
  - 同步注册 `DynamicDialectSupport`（`@Order(LOWEST/2)` 对齐 BS）
  - `bean-searcher.sql.dialect` 冲突 WARN
- [x] `ScaffoldMetaResolver.buildTables`：非 MySQL 系计划类带 `useIndex`（主表/关联表）启动期抛 `IllegalStateException`
- [x] imports 注册两个自动配置

### Step 4：starter-data-jpa — Hibernate platform 派生
- [x] `DynamicDataSourceProperties.Conf.dbType` 字段（DatabaseType 枚举）
- [x] `HibernateDialectPostProcessor`（BFPP）：显式配置永远优先；primary 块内 → 全局 → 注入
      `spring.jpa.properties.hibernate.dialect`（addLast）；DAMENG classpath 探测
- [x] metadata：`scaffold.database.db-type` + `datasources.<name>.db-type` 补全提示

### Step 5：测试与回归
- [x] `PaginationDialectTest`（8）：两组片段 + 解析链四态
- [x] `ScaffoldDialectConfigurationTest`（5）：三态优先级 / 单库 / 多库 DynamicDialect
- [x] `UseIndexGuardTest`（4）：达梦/金仓拒绝，MySQL 放行，无 useIndex 不受影响
- [x] `HibernateDialectPostProcessorTest`（7）：显式优先 / 派生 / primary 内联 / 达梦缺 jar 跳过
- [x] `DatabaseTypeTest`（5）
- [x] 全仓库 `mvn test`：26 模块 BUILD SUCCESS（common 68 / data-jpa 29 / query 44）

## 5. 业务端用法速查

```yaml
# 单库金仓：一行
scaffold:
  database:
    db-type: KINGBASE

# 多库：默认库兜底 + 异构库各自内联
scaffold:
  database:
    db-type: MYSQL
  datasource:
    dynamic:
      datasources:
        master:    { url: jdbc:mysql://... }
        cemetery:  { db-type: KINGBASE, url: jdbc:kingbase8://... }
        archive:   { db-type: DAMENG,  url: jdbc:dm://... }
```

| 运行时上下文 | 命中类型 |
|---|---|
| 未切数据源 / `@TargetDataSource("master")` / doNativeQuery | master（或全局） |
| `@TargetDataSource("cemetery")` / `TableX(dataSource = "cemetery")` | cemetery 内联声明 |
| JPA（EntityManagerFactory 绑定 primary 库） | primary 块内声明（单库即全局） |

## 6. 边界与明确不做

1. **不做 URL 自动推断**——Spring Boot 2.7 DatabaseDriver 不含国产库，业界无先例，推断必错。
2. **不做"各通路各自配方言"**——本方案存在的意义就是消灭它。
3. **SQL Server FETCH 组**：SQL Server 2012 要求语句带 ORDER BY（达梦/Oracle 无此限制），
   手写 SQL 命中 SQL Server 时需保证含排序子句（`PaginationDialect` javadoc 已注明）。
4. **OceanBase 按 MySQL 模式处理**（绝对主流）；Oracle 模式的项目直接声明 `ORACLE`。
5. **达梦 JPA dialect** 依赖社区方言 jar，缺失时 WARN 跳过不注入；用户可显式配
   `spring.jpa.database-platform` 兜底（显式永远优先）。
6. **宿主自建 NamedDataSource 不走 dynamic 配置**时，其类型按全局 `db-type` 兜底（避免 DynamicDialect lookup 抛异常）。
7. `spring.jpa.properties.hibernate.dialect` 显式配置同样优先于派生。

## 7. 后续可选增强（非本期）

- dynamic 组件 `Conf` 增加 driver-class-name 自动推断提示（同 BAOMIDOU 体验）
- 达梦社区方言坐标写入 BOM 可选依赖（等真实项目驱动）
- observability 暴露各数据源 db-type 供运维检查
