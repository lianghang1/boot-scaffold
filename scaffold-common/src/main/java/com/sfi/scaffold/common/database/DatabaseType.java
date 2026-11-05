package com.sfi.scaffold.common.database;

/**
 * 数据库类型枚举：本系统连接的数据库是什么，由用户一处声明（{@code scaffold.database.db-type}，
 * 多库时声明在各数据源块的 {@code db-type}），各 SQL 通路自行派生方言，禁止各通路各配一种方言。
 *
 * <p>国产库无法从 JDBC URL 可靠推断（Spring Boot 2.7 DatabaseDriver 不含国产库），
 * 业界主流框架（MyBatis-Plus DbType、PageHelper、Hibernate）也全部要求显式声明，
 * 故本枚举只做"声明 → 方言"的映射，不做推断。</p>
 *
 * <p>方言组折叠（业界通行做法）：金仓/openGauss 兼容 PostgreSQL（{@code LIMIT ... OFFSET ...}），
 * 达梦兼容 Oracle 12c（{@code OFFSET ... ROWS FETCH NEXT ... ROWS ONLY}）。</p>
 *
 * @author lianghang
 */
public enum DatabaseType {

    /** MySQL 及其兼容系 */
    MYSQL,

    /** PostgreSQL */
    POSTGRESQL,

    /** Oracle 12c+ */
    ORACLE,

    /** SQL Server 2012+ */
    SQLSERVER,

    /** 达梦 DM8（Oracle 兼容） */
    DAMENG,

    /** 人大金仓 KingbaseES（PostgreSQL 兼容） */
    KINGBASE,

    /** 华为 openGauss / GaussDB（PostgreSQL 兼容） */
    GAUSS,

    /** OceanBase（MySQL 模式，绝对主流；Oracle 模式的项目请直接声明 ORACLE） */
    OCEANBASE,

    /** 未识别类型，按 MySQL 系处理 */
    UNKNOWN;

    /**
     * 分页方言组：
     * <ul>
     *   <li>LIMIT — {@code LIMIT :limit OFFSET :offset}（MySQL/PG/金仓/openGauss/OceanBase）</li>
     *   <li>FETCH — {@code OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY}（Oracle/SQL Server 2012+/达梦）</li>
     * </ul>
     */
    public enum PaginationGroup {LIMIT, FETCH}

    public PaginationGroup paginationGroup() {
        switch (this) {
            case ORACLE:
            case SQLSERVER:
            case DAMENG:
                return PaginationGroup.FETCH;
            default:
                return PaginationGroup.LIMIT;
        }
    }

    /**
     * 是否 MySQL 系（决定 MySQL 专有语法是否可用，如 {@code FORCE INDEX}）。
     */
    public boolean isMySqlFamily() {
        return this == MYSQL || this == OCEANBASE;
    }

    /**
     * Hibernate dialect 类名（Hibernate 5.6 / Spring Boot 2.7）。
     * 返回 null 表示主流数据库，交给 Hibernate 自识别，脚手架不注入。
     * 达梦返回社区方言类名，消费方需先做 classpath 探测，缺失时告警跳过。
     */
    public String hibernatePlatform() {
        switch (this) {
            case KINGBASE:
            case GAUSS:
                return "org.hibernate.dialect.PostgreSQL10Dialect";
            case OCEANBASE:
                return "org.hibernate.dialect.MySQL57Dialect";
            case DAMENG:
                return "org.hibernate.dialect.DmDialect";
            default:
                return null;
        }
    }
}
