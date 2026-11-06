package com.sfi.scaffold.starter.liquibase.config;

import liquibase.integration.spring.SpringLiquibase;

import java.util.Collections;
import java.util.List;

/**
 * 多数据源 Liquibase 迁移的执行标记 Bean：持有本启动周期已执行完成的各库 SpringLiquibase 实例，
 * 供宿主与测试断言。迁移执行发生在 Bean 创建期（启动线程内），失败即启动失败（与官方单库语义一致）。
 *
 * <p>继承 {@link SpringLiquibase} 仅为类型标记：本 Bean 先于官方 {@code LiquibaseAutoConfiguration}
 * 注册，官方装配的 {@code @ConditionalOnMissingBean(SpringLiquibase.class)} 依此识别并退让，
 * 避免官方以默认 changelog 路径抢跑。本体不执行任何迁移，{@link #afterPropertiesSet()}
 * 置空以拦截容器回调走到父类的单库执行逻辑。</p>
 */
public class LiquibaseRunner extends SpringLiquibase {

    private final List<SpringLiquibase> liquibases;

    public LiquibaseRunner(List<SpringLiquibase> liquibases) {
        this.liquibases = Collections.unmodifiableList(liquibases);
    }

    /** 本体不执行迁移——真实迁移已在各库实例的 Bean 创建期完成，不调用父类单库逻辑。 */
    @Override
    public void afterPropertiesSet() {
        // 类型标记 Bean，容器回调不应触发父类的单库迁移执行
    }

    /** 各库迁移实例（顺序 = 配置声明顺序）。 */
    public List<SpringLiquibase> getLiquibases() {
        return liquibases;
    }
}
