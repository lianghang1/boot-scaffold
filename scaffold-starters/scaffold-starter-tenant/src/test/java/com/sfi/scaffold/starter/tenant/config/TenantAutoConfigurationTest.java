package com.sfi.scaffold.starter.tenant.config;

import com.sfi.scaffold.starter.tenant.aspect.FindByIdTenantAspect;
import com.sfi.scaffold.starter.tenant.aspect.JpaTenantFilterAspect;
import com.sfi.scaffold.starter.tenant.aspect.TenantIgnoreAspect;
import com.sfi.scaffold.starter.tenant.bs.TenantParamFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.persistence.EntityManagerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装配条件测试：opt-in 默认零装配；开启后豁免切面恒装配、BS 通路按 classpath 装配；
 * JPA 通路需要事务管理器，其装配由 {@code JpaTenantAspectTest} 的完整上下文覆盖。
 *
 * @author lianghang
 */
class TenantAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantAutoConfiguration.class))
            .withBean(PlatformTransactionManager.class, NoopTransactionManager::new)
            // 模拟 data-jpa 自动配置提供的 EMF（@PersistenceContext 注入所需）
            .withBean(EntityManagerFactory.class, () -> org.mockito.Mockito.mock(EntityManagerFactory.class));

    /** 模拟 data-jpa 自动配置提供的事务管理器（仅验证装配条件） */
    static class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    @Test
    void disabledByDefaultShouldAssembleNothing() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(TenantIgnoreAspect.class);
            assertThat(context).doesNotHaveBean(TenantParamFilter.class);
            assertThat(context).doesNotHaveBean(JpaTenantFilterAspect.class);
            assertThat(context).doesNotHaveBean(FindByIdTenantAspect.class);
        });
    }

    @Test
    void enabledShouldAssembleIgnoreAspectAndBsFilter() {
        runner.withPropertyValues("scaffold.tenant.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(TenantIgnoreAspect.class);
            // 测试 classpath 有 bean-searcher（经 query optional 依赖）→ BS 过滤器装配
            assertThat(context).hasSingleBean(TenantParamFilter.class);
            // 生产环境 data-jpa 自动配置必然提供事务管理器（afterName 保证顺序），
            // 裸 runner 手动补 no-op 模拟该前提
            assertThat(context).hasSingleBean(JpaTenantFilterAspect.class);
            assertThat(context).hasSingleBean(FindByIdTenantAspect.class);
        });
    }

    @Test
    void ignoreTablesShouldBindFromConfiguration() {
        runner.withPropertyValues(
                        "scaffold.tenant.enabled=true",
                        "scaffold.tenant.ignore-tables[0]=t_dict",
                        "scaffold.tenant.ignore-tables[1]=t_config")
                .run(context -> {
                    TenantProperties properties = context.getBean(TenantProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getIgnoreTables()).containsExactly("t_dict", "t_config");
                });
    }
}
