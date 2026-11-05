package com.sfi.scaffold.starter.liquibase.config;

import liquibase.integration.spring.SpringLiquibase;

import java.util.Collections;
import java.util.List;

/**
 * 多数据源 Liquibase 迁移的执行标记 Bean：持有本启动周期已执行完成的各库 SpringLiquibase 实例，
 * 供宿主与测试断言。迁移执行发生在 Bean 创建期（启动线程内），失败即启动失败（与官方单库语义一致）。
 */
public class LiquibaseRunner {

    private final List<SpringLiquibase> liquibases;

    public LiquibaseRunner(List<SpringLiquibase> liquibases) {
        this.liquibases = Collections.unmodifiableList(liquibases);
    }

    /** 各库迁移实例（key 顺序 = 配置声明顺序）。 */
    public List<SpringLiquibase> getLiquibases() {
        return liquibases;
    }
}
