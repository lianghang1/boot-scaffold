# scaffold-starter-tenant 多租户方案（已实施）

> 状态：**已实施**（2026-11-06，全模块测试 24/24 绿）；实施过程中的两处实测修订见 D7/D13；实施后的接入形态讨论收口见 D15。
> 决策依据：2026-11 调研（芋道官方文档、Bean Searcher 作者 issue 答复、Hibernate 官方文档、JeecgBoot/RuoYi-Vue-Plus 对照）。

## 1. 决策记录

| # | 决策 | 结论 |
|---|---|---|
| D1 | 隔离模式 | **字段隔离**（业务表含 tenant_id 列），不做分库/Schema/物理隔离 |
| D2 | 直连 SQL 边界 | 业务绕过脚手架直接用 JdbcTemplate 写 SQL **不管租户**——使用者自知（业主拍板） |
| D3 | 管理面 | 租户开通/套餐/租户内角色归业务系统，脚手架**只保证执行层不漏**，不建租户表 |
| D4 | 隔离语义 | 默认全过滤（黑名单语义：未排除即过滤），排除走配置名单 + 注解 |
| D5（修订） | 用户上下文 | tenantId 为 `LoginUser`/`SessionInfo` **一线字段**（String，null = 平台身份）；`attributes` 扩展槽已实现（`Map<String, String>`，值统一 String 防会话 JSON 反序列化类型退化；随 `from/toLoginUser` 互转透传与 `TenantScope` 切换保留），业务自定义字段（部门/等级等）走此槽 |
| D6 | 开关模式 | **opt-in**：`scaffold.tenant.enabled` 默认 false；未引 starter / 未开启时三通路零影响 |
| D7（修订） | 列名与类型 | 列名**不做配置级重命名**：JPA 由实体 `@Filter` condition 声明、BS 由计划类字段映射声明（Hibernate 5.6 机制所限，配置列名在两条通路均无处落地）；值类型脚手架不约束（String 承载） |
| D8（修订） | 登录获取 | 框架不假设业务用户表：业务登录逻辑查表带出租户放入 SessionInfo.tenantId（一线字段），框架只负责传递与注入 |
| D9' | findById 绕过过滤 | 过滤式切面：租户不一致返回空 + WARN 审计（防存在性探测，非 403），`@TenantIgnore` 豁免 |
| D13（修订） | JPA 接入形态 | 实体**直写** `@Filter(name = "scaffold_tenant", condition = "tenant_id = :tenantId")` + 可选 `@TenantIdField`（findById 比对用）。实测 Hibernate 5.6 **不支持 @Filter 的 meta-annotation 组合**（原 @TenantFilter 组合注解方案被测试推翻，已删除） |
| D14 | 租户上下文承载 | **不另立 TenantContext TTL**：租户上下文 = `LoginContext`（tenantId 一线字段随身份装载）；定时任务/MQ 用 `LoginContext.set` + `TenantScope.execute(tenantId, runnable)`（try/finally 防泄漏）；`@TenantIgnore` 豁免标记用 tenant starter 内独立 ThreadLocal |
| D15 | 接入形态收口 | **保持现状，不做增强**：`@Filter`（实体）/ tenantId 字段（计划类）即全部声明，与其他映射标注（`@Entity`/`@ColumnX`）同级别的心智成本。讨论中评估并**否决**的方案：TenantEntity/TenantPlan 基类（继承也是声明动作，负担只是换形态）、EMF 元数据扫描注册表 + WARN 对账（用一台机器防一个低风险事件，成本盖过收益）、启动期 fail-fast 校验（业主否决：等于强制）、ArchUnit 测试期卡点（依赖业务测试基建）。心智模型一句话：**数据在哪，映射注解就写在哪** |

## 2. 架构：新增 scaffold-starter-tenant

### 2.1 上下文层（D14：租户上下文 = LoginContext，不另立 TTL）

- 无 `TenantContext`、无 `TenantContextFilter`：tenantId 为 `LoginUser`/`SessionInfo` 一线字段（D5 修订），认证过滤器从 Redis 会话重建身份时**租户自动就位**——装载零新增组件；无登录态或 tenantId 为 null = 平台视角（不拼条件）
- 读取：注入点统一取 `LoginContext.currentOrNull().getTenantId()`；TTL 底座天然保证 `@Async` / 自建线程池（ScaffoldExecutors）子线程租户不丢失、不串号
- 模拟租户（定时任务、MQ 消费）：业务构造带 tenantId 的身份 `LoginContext.set(...)`（先例 = `LoginUser.system()`）；tenant starter 提供 `TenantScope.execute(tenantId, runnable)`——try/finally 里 set/恢复现场，防止忘记 clear 沿线程池泄漏；`set` 会覆盖整个身份并同步审计人（job/MQ 本为合成身份，无影响）

### 2.2 执行层：三条通路注入

| 通路 | 机制 | 接入形态 |
|---|---|---|
| data-jpa | Hibernate `@FilterDef/@Filter` + AOP 环绕（每事务 `session.enableFilter("scaffold_tenant")`，参数取 `LoginContext` 中的 tenantId） | **实体显式标注** `@Filter`（Hibernate 5.6 机制所限，白名单式）|
| Bean Searcher | 全局 `ParamFilter`（官方一等能力，作者 issue I7KE1V 方案）：检测 BeanMeta 是否含 tenantId 字段，有则注入条件，join 自动传导主表 | **字段探测自动接入**，标记接口/注解可排除 |
| doNativeQuery(sql) | **不注入**——纯 SQL 字符串入口（L331/L375），框架不解析手写 SQL，无表/别名信息；租户条件由业务写进 SQL（D2 同位阶），javadoc 显著声明 | 手写 SQL 自负 |

### 2.3 排除体系（黑名单语义）

- 配置：`scaffold.tenant.ignore-tables`（表名列表，作用于 BS ParamFilter 的注入排除；JPA 由实体标注天然区分，doNativeQuery(sql) 不注入无需排除）
- 注解：`@TenantIgnore`（两个豁免通道，作用于**自动注入的查询**：① service 方法上——豁免标记（tenant starter 内独立 ThreadLocal），方法内计划类查询/JPA/findById 全部豁免；② 计划类上——该计划所有查询豁免；另平台身份（上下文无租户）天然全放行。doNativeQuery(sql) 本就不注入，无需豁免）
- 参照芋道：忽略系统表（字典/配置/租户映射本身）

### 2.4 首发不做（记录）

- `@TenantJob` 按租户并行跑任务、MQ 消息头透传——首发不做，接口预留（`TenantScope.execute` 已覆盖模拟场景）
- Redis key 租户后缀——**不需要**：幂等/限流键已含 accountId（全局唯一），跨租户天然无碰撞
- SaaS 多域名解析——网关/前端定租户后走请求头，脚手架只收 header
- 物理隔离——已有 dynamic 多数据源能力，业务自行组合；已知坑（连接池膨胀、事务内切库）写入文档劝退
- 接入形态增强（D15，全部否决记录在案，防止后人重走）：TenantEntity/TenantPlan 基类、租户表注册表 + WARN 对账、启动期/测试期校验——"忘写一行"的后果与忘写其他映射标注同级，不为它引入框架级机器

### 2.5 已实测的机制边界（防重走弯路）

1. **Hibernate 5.6 不展开 @Filter 的 meta-annotation**——自定义组合注解（如 @TenantFilter 内标 @Filter）在实体上静默不生效（测试证伪），必须直写 `@Filter`
2. **BS 条件注入必须依托计划类字段**——ParamFilter 无法凭空注入没有字段映射的条件；计划类漏写 tenantId 字段时框架无法感知该表"应过滤"（脚手架不建租户表，判定信息只能来自声明）
3. **注解值是编译期常量**——`@Filter` condition 列名不可由配置注入，故列名无配置项（D7）

## 3. 边界声明（写入文档与 README）

1. 直连 JdbcTemplate / `doNativeQuery(sql)` / `doNativeCount(sql)`（纯 SQL 字符串入口）：**框架不注入**（D2 同位阶，框架不解析手写 SQL），租户条件业务自含，javadoc 声明
2. BS 内联子查询（`@SearchBean(tables="(SELECT ...) t")`）：**子查询内部**不过滤，需自行带 tenant 条件
3. `findById` 按主键直取绕过 `@Filter`（Hibernate 5.6 硬伤）：由 **findById 过滤切面**补齐（决策 D9'：租户不一致返回空 + WARN 审计，`@TenantIgnore` 豁免）
4. ~~复杂聚合/子查询的 doNativeQuery：主表注入可靠~~（已废弃：doNativeQuery 为纯 SQL 入口，不注入，见边界 1）

## 4. 疑问收口（全部已决，结论以 §1 决策记录为准）

| 原疑问 | 结论 |
|---|---|
| Q2 上下文来源冲突 | **已消解**：上下文唯一来源 = 登录身份，请求头来源砍掉（D8 修订版），冲突场景不存在 |
| Q3 findById 绕过过滤 | 过滤式切面：不一致返回空 + WARN（防存在性探测，非 403），`@TenantIgnore` 豁免（D9'） |
| Q4 JPA 逐实体标注 | 接受；实施修订为直写 `@Filter` + `@TenantIdField`（D13 修订，组合注解实测不生效） |
| doNativeQuery 能否不受租户限制 | 能：方法/计划类 `@TenantIgnore` 或平台身份，见 §2.3 |

## 5. 工作量（实施记录）

LoginUser/SessionInfo tenantId 一线字段（~20 行）→ tenant starter：TenantParamFilter（BS）、JpaTenantFilterAspect + FindByIdTenantAspect（JPA）、@TenantIgnore/@TenantIdField、TenantIgnoreHolder（深度计数）、TenantScope、TenantProperties/AutoConfiguration；BaseEntity 增挂 `scaffold_tenant` FilterDef；测试 24 个（单元 + H2 双通路集成 + 装配条件）全绿。
