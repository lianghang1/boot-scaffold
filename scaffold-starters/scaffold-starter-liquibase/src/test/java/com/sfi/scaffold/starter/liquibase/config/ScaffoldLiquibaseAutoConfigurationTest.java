package com.sfi.scaffold.starter.liquibase.config;

import com.sfi.scaffold.common.database.DatabaseType;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.util.HashMap;
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
}
