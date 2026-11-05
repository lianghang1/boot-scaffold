package com.sfi.scaffold.starter.liquibase.config;

import com.sfi.scaffold.starter.liquibase.database.ScaffoldDatabaseRegistrar;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多数据源 Liquibase 集成：为数据源块内联了 {@code liquibase.change-log} 的每个库
 * 组装并执行一个 {@link SpringLiquibase}。
 *
 * <p>边界（与 [liquibase-starter-plan] 一致）：
 * <ul>
 *   <li>单库场景不介入——官方 {@link LiquibaseAutoConfiguration} 在单 DataSource 候选下已注册
 *       SpringLiquibase，本配置 {@code @ConditionalOnMissingBean(SpringLiquibase.class)} 自然退让；
 *       多库场景官方因 {@code @ConditionalOnSingleCandidate(DataSource)} 不注册，由本配置接管</li>
 *   <li>宿主自定义 SpringLiquibase Bean 时本配置退让</li>
 *   <li>数据源池从 {@link AbstractRoutingDataSource#getResolvedDataSources()} 取（spring-jdbc
 *       官方 API，不依赖 data-jpa 实现类）</li>
 *   <li>迁移执行在启动线程内，任一库失败即启动失败（与官方单库语义一致）</li>
 * </ul>
 */
@AutoConfigureAfter(LiquibaseAutoConfiguration.class)
@ConditionalOnClass(SpringLiquibase.class)
public class ScaffoldLiquibaseAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldLiquibaseAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(SpringLiquibase.class)
    @Conditional(ScaffoldLiquibaseCondition.class)
    public LiquibaseRunner scaffoldLiquibaseRunner(ObjectProvider<AbstractRoutingDataSource> routingProvider,
                                                   Environment env) {
        Map<String, String> changelogs = LiquibaseDatabaseDeclarations.changelogs(env);
        AbstractRoutingDataSource routing = routingProvider.getIfAvailable();
        if (routing == null) {
            throw new IllegalStateException("scaffold-starter-liquibase: 检测到数据源 liquibase.change-log 配置 "
                    + changelogs.keySet() + "，但容器中不存在 AbstractRoutingDataSource（多数据源未启用），无法执行迁移");
        }
        warnIfDdlAuto(env);
        // 迁移执行前先按声明注册自定义 Database（金仓/openGauss/OceanBase 借壳实现、达梦扩展检查）
        ScaffoldDatabaseRegistrar.registerForDeclarations(env);
        List<SpringLiquibase> liquibases = buildSpringLiquibases(routing, changelogs);
        executeAll(liquibases);
        return new LiquibaseRunner(liquibases);
    }

    /** 组装各库迁移实例（仅组装不执行，供测试断言归属）。 */
    static List<SpringLiquibase> buildSpringLiquibases(AbstractRoutingDataSource routing,
                                                       Map<String, String> changelogs) {
        Map<Object, DataSource> resolved = routing.getResolvedDataSources();
        List<SpringLiquibase> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : changelogs.entrySet()) {
            String name = entry.getKey();
            DataSource ds = resolved.get(name);
            if (ds == null) {
                throw new IllegalStateException("scaffold-starter-liquibase: 数据源 [" + name
                        + "] 未在路由数据源中注册（getResolvedDataSources 无此 key），无法执行 Liquibase 迁移");
            }
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setDataSource(ds);
            liquibase.setChangeLog(entry.getValue());
            result.add(liquibase);
        }
        return result;
    }

    private static void executeAll(List<SpringLiquibase> liquibases) {
        for (SpringLiquibase liquibase : liquibases) {
            String changeLog = liquibase.getChangeLog();
            log.info("scaffold-starter-liquibase: 执行 Liquibase 迁移 changeLog={}", changeLog);
            try {
                liquibase.afterPropertiesSet();
            } catch (LiquibaseException e) {
                throw new IllegalStateException("scaffold-starter-liquibase: Liquibase 迁移失败 changeLog="
                        + changeLog + "：" + e.getMessage(), e);
            } catch (RuntimeException e) {
                throw new IllegalStateException("scaffold-starter-liquibase: Liquibase 迁移失败 changeLog="
                        + changeLog + "：" + e.getMessage(), e);
            }
        }
    }

    private static void warnIfDdlAuto(Environment env) {
        String ddlAuto = env.getProperty("spring.jpa.hibernate.ddl-auto");
        if (ddlAuto != null && !"none".equalsIgnoreCase(ddlAuto)) {
            log.warn("scaffold-starter-liquibase: 检测到 spring.jpa.hibernate.ddl-auto={}，"
                    + "schema 管理权应归 Liquibase，建议显式置为 none 以避免两者互相覆盖", ddlAuto);
        }
    }

    /** 任一数据源块内联了 liquibase.change-log 才激活。 */
    static class ScaffoldLiquibaseCondition implements ConfigurationCondition {
        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }

        @Override
        public boolean matches(ConditionContext context, org.springframework.core.type.AnnotatedTypeMetadata metadata) {
            return !LiquibaseDatabaseDeclarations.changelogs(context.getEnvironment()).isEmpty();
        }
    }
}
