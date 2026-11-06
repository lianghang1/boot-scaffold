package com.sfi.scaffold.starter.liquibase.config;

import com.sfi.scaffold.common.database.DatabaseType;
import liquibase.integration.spring.SpringLiquibase;
import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.mock.env.MockEnvironment;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 自动装配行为：未配置零 Bean / 条件激活 / 组装归属 / 缺路由数据源 fail-fast，
 * 以及 Environment 声明解析链。
 */
class ScaffoldLiquibaseAutoConfigurationTest {

    private static final String CL_CEMETERY = "classpath:db/cemetery.yaml";
    private static final String CL_MASTER = "classpath:db/master.yaml";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ScaffoldLiquibaseAutoConfiguration.class));

    // ---- 条件与退让 ----

    @Test
    void noConfigurationCreatesNoBeans() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(LiquibaseRunner.class);
            assertThat(context).doesNotHaveBean(SpringLiquibase.class);
        });
    }

    @Test
    void changelogWithoutRoutingDataSourceFailsFast() {
        runner.withPropertyValues(
                        "scaffold.datasource.dynamic.datasources.cemetery.liquibase.change-log=" + CL_CEMETERY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure());
                    assertThat(root).isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("AbstractRoutingDataSource");
                });
    }

    // ---- 组装归属（build 阶段静态验证，不触发真实迁移） ----

    @Test
    void buildsOneSpringLiquibasePerConfiguredDataSource() {
        AbstractRoutingDataSource routing = mock(AbstractRoutingDataSource.class);
        DataSource masterDs = mock(DataSource.class);
        DataSource cemeteryDs = mock(DataSource.class);
        Map<Object, DataSource> resolved = new HashMap<>();
        resolved.put("master", masterDs);
        resolved.put("cemetery", cemeteryDs);
        when(routing.getResolvedDataSources()).thenReturn(resolved);

        Map<String, String> changelogs = new HashMap<>();
        changelogs.put("master", CL_MASTER);
        changelogs.put("cemetery", CL_CEMETERY);

        Map<String, SpringLiquibase> built = new HashMap<>();
        for (SpringLiquibase lb : ScaffoldLiquibaseAutoConfiguration.buildSpringLiquibases(routing, changelogs)) {
            built.put(lb.getChangeLog(), lb);
        }

        assertThat(built).containsKeys(CL_MASTER, CL_CEMETERY);
        assertThat(built.get(CL_MASTER).getDataSource()).isSameAs(masterDs);
        assertThat(built.get(CL_CEMETERY).getDataSource()).isSameAs(cemeteryDs);
    }

    @Test
    void failsFastWhenConfiguredDataSourceNotResolved() {
        AbstractRoutingDataSource routing = mock(AbstractRoutingDataSource.class);
        when(routing.getResolvedDataSources()).thenReturn(new HashMap<>());

        Map<String, String> changelogs = new HashMap<>();
        changelogs.put("cemetery", CL_CEMETERY);

        org.assertj.core.api.Assertions.assertThatIllegalStateException()
                .isThrownBy(() -> ScaffoldLiquibaseAutoConfiguration.buildSpringLiquibases(routing, changelogs))
                .withMessageContaining("cemetery");
    }

    // ---- Environment 声明解析链 ----

    @Test
    void declarationsFollowOverrideChain() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.database.db-type", "MYSQL");
        env.setProperty("scaffold.datasource.dynamic.datasources.cemetery.db-type", "KINGBASE");
        env.setProperty("scaffold.datasource.dynamic.datasources.cemetery.liquibase.change-log", CL_CEMETERY);
        env.setProperty("scaffold.datasource.dynamic.datasources.master.url", "jdbc:mysql://db");

        assertThat(LiquibaseDatabaseDeclarations.globalType(env)).isEqualTo(DatabaseType.MYSQL);
        assertThat(LiquibaseDatabaseDeclarations.dataSourceTypes(env)).containsEntry("cemetery", DatabaseType.KINGBASE);
        assertThat(LiquibaseDatabaseDeclarations.declaredTypes(env))
                .containsExactlyInAnyOrder(DatabaseType.MYSQL, DatabaseType.KINGBASE);
        assertThat(LiquibaseDatabaseDeclarations.changelogs(env)).containsEntry("cemetery", CL_CEMETERY);
    }

    @Test
    void changelogOnlyForBlocksThatDeclareIt() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.datasource.dynamic.datasources.master.liquibase.change-log", CL_MASTER);
        env.setProperty("scaffold.datasource.dynamic.datasources.reader.liquibase.change-log", CL_CEMETERY);

        assertThat(LiquibaseDatabaseDeclarations.changelogs(env))
                .containsOnlyKeys("master", "reader");
    }

    // ---- 与官方 LiquibaseAutoConfiguration 共存（cemlink 启动故障回归场景） ----

    private final ApplicationContextRunner officialRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ScaffoldLiquibaseAutoConfiguration.class, LiquibaseAutoConfiguration.class));

    @Test
    void officialLiquibaseBacksOffAndMigrationsRunOnEachDatabase() {
        SimpleDriverDataSource master = h2("master");
        SimpleDriverDataSource cemetery = h2("cemetery");

        officialRunner
                .withBean("routingDataSource", AbstractRoutingDataSource.class, () -> fixedRouting(master, cemetery))
                .withPropertyValues(
                        "scaffold.datasource.dynamic.datasources.master.liquibase.change-log=classpath:db/test/master.xml",
                        "scaffold.datasource.dynamic.datasources.cemetery.liquibase.change-log=classpath:db/test/cemetery.xml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // runner 以 SpringLiquibase 子类注册，官方 @ConditionalOnMissingBean 退让，
                    // 容器中 SpringLiquibase 类型只有 runner 本体，官方默认路径抢跑不复存在
                    assertThat(context.getBeansOfType(SpringLiquibase.class)).hasSize(1);
                    assertThat(context).hasBean("scaffoldLiquibaseRunner");
                    // 两个库的真实迁移均已执行
                    assertThat(tableExists(master, "T_MASTER")).isTrue();
                    assertThat(tableExists(cemetery, "T_CEMETERY")).isTrue();
                });
    }

    @Test
    void entityManagerFactoryGetsDependsOnMigrationRunner() {
        SimpleDriverDataSource master = h2("master");

        officialRunner
                .withBean("routingDataSource", AbstractRoutingDataSource.class, () -> fixedRouting(master, null))
                .withBean("entityManagerFactory", TestEntityManagerFactoryBean.class)
                .withPropertyValues(
                        "scaffold.datasource.dynamic.datasources.master.liquibase.change-log=classpath:db/test/master.xml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeanFactory()
                            .getBeanDefinition("entityManagerFactory").getDependsOn())
                            .contains("scaffoldLiquibaseRunner");
                });
    }

    @Test
    void dependencyEdgeSkippedWhenRunnerAbsent() {
        SimpleDriverDataSource master = h2("master");

        // 宿主自定义 SpringLiquibase → runner 退让 → 依赖边不得指向缺失的 Bean（否则 EMF 创建失败）
        officialRunner
                .withBean("routingDataSource", AbstractRoutingDataSource.class, () -> fixedRouting(master, null))
                .withBean("hostLiquibase", SpringLiquibase.class, () -> {
                    SpringLiquibase liquibase = new SpringLiquibase();
                    liquibase.setShouldRun(false);
                    return liquibase;
                })
                .withBean("entityManagerFactory", TestEntityManagerFactoryBean.class)
                .withPropertyValues(
                        "scaffold.datasource.dynamic.datasources.master.liquibase.change-log=classpath:db/test/master.xml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("scaffoldLiquibaseRunner");
                    assertThat(context.getBeanFactory()
                            .getBeanDefinition("entityManagerFactory").getDependsOn())
                            .isNull();
                });
    }

    private static SimpleDriverDataSource h2(String name) {
        SimpleDriverDataSource ds = new SimpleDriverDataSource();
        ds.setDriverClass(Driver.class);
        ds.setUrl("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    private static AbstractRoutingDataSource fixedRouting(DataSource master, DataSource cemetery) {
        Map<Object, Object> targets = new LinkedHashMap<>();
        targets.put("master", master);
        if (cemetery != null) {
            targets.put("cemetery", cemetery);
        }
        AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return "master";
            }
        };
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(master);
        routing.afterPropertiesSet();
        return routing;
    }

    private static boolean tableExists(DataSource ds, String table) throws SQLException {
        try (Connection connection = ds.getConnection();
             ResultSet rs = connection.getMetaData().getTables(null, null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /** 类型探针：验证依赖边按 EntityManagerFactory 类型识别 FactoryBean 产生的 EMF。 */
    static class TestEntityManagerFactoryBean implements org.springframework.beans.factory.FactoryBean<EntityManagerFactory> {
        @Override
        public EntityManagerFactory getObject() {
            return mock(EntityManagerFactory.class);
        }

        @Override
        public Class<?> getObjectType() {
            return EntityManagerFactory.class;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }
}
