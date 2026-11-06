package com.sfi.scaffold.starter.liquibase.config;

import com.sfi.scaffold.starter.liquibase.database.ScaffoldDatabaseRegistrar;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多数据源 Liquibase 集成：为数据源块内联了 {@code liquibase.change-log} 的每个库
 * 组装并执行一个 {@link SpringLiquibase}。
 *
 * <p>与官方 {@link LiquibaseAutoConfiguration} 的共存规则：
 * <ul>
 *   <li>单库场景不介入——数据源块未内联 {@code change-log} 时本配置不激活，
 *       官方 {@code spring.liquibase.*} 照常工作</li>
 *   <li>多库场景先于官方注册——路由数据源是容器中唯一的 DataSource 候选，官方装配同样激活；
 *       若官方注册 {@link SpringLiquibase}，会以默认 changelog 路径
 *       （{@code classpath:/db/changelog/db.changelog-master.yaml}）执行并启动失败。
 *       故本配置先于官方装配，runner 以 SpringLiquibase 子类注册（见 {@link LiquibaseRunner}），
 *       官方 {@code @ConditionalOnMissingBean(SpringLiquibase.class)} 自然退让</li>
 *   <li>宿主自定义 SpringLiquibase Bean 时本配置退让</li>
 * </ul>
 *
 * <p>顺序保证：{@link MigrationBeforeEntityManagerFactoryPostProcessor} 为容器内全部
 * {@link EntityManagerFactory} 补 dependsOn runner 的依赖边，迁移先于 Hibernate schema
 * 校验（validate）执行——Boot 2.7 的数据库初始化依赖机制只内置了 Flyway 检测器，未覆盖 Liquibase。</p>
 *
 * <p>边界：
 * <ul>
 *   <li>数据源池从 {@link AbstractRoutingDataSource#getResolvedDataSources()} 取（spring-jdbc
 *       官方 API，不依赖 data-jpa 实现类）</li>
 *   <li>迁移执行在启动线程内，任一库失败即启动失败（与官方单库语义一致）</li>
 * </ul>
 */
@AutoConfigureBefore(LiquibaseAutoConfiguration.class)
@ConditionalOnClass(SpringLiquibase.class)
public class ScaffoldLiquibaseAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldLiquibaseAutoConfiguration.class);

    /** 依赖边 Bean：runner 被宿主 SpringLiquibase 顶替时自动空转（guards 在其内部）。 */
    @Bean
    static MigrationBeforeEntityManagerFactoryPostProcessor migrationBeforeEntityManagerFactoryPostProcessor() {
        return new MigrationBeforeEntityManagerFactoryPostProcessor();
    }

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
        // validate 与迁移先后顺序已有依赖边保证，属安全用法；create*/update 会与迁移互相覆盖 schema
        if (ddlAuto != null && !"none".equalsIgnoreCase(ddlAuto) && !"validate".equalsIgnoreCase(ddlAuto)) {
            log.warn("scaffold-starter-liquibase: 检测到 spring.jpa.hibernate.ddl-auto={}，"
                    + "会与 Liquibase 迁移互相覆盖 schema，建议改为 validate（框架已保证迁移先于校验）或 none", ddlAuto);
        }
    }

    /**
     * 为容器内全部 {@link EntityManagerFactory} Bean 增加 dependsOn 迁移 runner 的依赖边，
     * 保证 Liquibase 迁移先于 Hibernate schema 校验（validate）执行。
     * runner 不存在（被宿主 SpringLiquibase 顶替）时空转，避免指向缺失 Bean 的悬空依赖。
     */
    static class MigrationBeforeEntityManagerFactoryPostProcessor implements BeanFactoryPostProcessor {

        private static final String RUNNER_BEAN_NAME = "scaffoldLiquibaseRunner";

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            if (!beanFactory.containsBeanDefinition(RUNNER_BEAN_NAME)) {
                return;
            }
            for (String name : beanFactory.getBeanNamesForType(EntityManagerFactory.class, true, false)) {
                BeanDefinition beanDefinition = beanFactory.getBeanDefinition(name);
                Set<String> merged = new LinkedHashSet<>(
                        beanDefinition.getDependsOn() != null
                                ? Arrays.asList(beanDefinition.getDependsOn())
                                : Collections.emptySet());
                merged.add(RUNNER_BEAN_NAME);
                beanDefinition.setDependsOn(merged.toArray(new String[0]));
            }
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
