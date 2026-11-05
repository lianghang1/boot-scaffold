package com.sfi.scaffold.query.config;

import cn.zhxu.bs.boot.BeanSearcherAutoConfiguration;
import cn.zhxu.bs.dialect.MySqlDialect;
import cn.zhxu.bs.dialect.PostgreSqlDialect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ScaffoldDialectConfiguration} 三态优先级测试：
 * 用户自定义 Dialect Bean &gt; 脚手架按 db-type 派生 &gt; BS 默认（bean-searcher.sql.dialect）。
 *
 * @author lianghang
 */
class ScaffoldDialectConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ScaffoldDialectConfiguration.class,
                    BeanSearcherAutoConfiguration.class));

    @Test
    void noDeclarationKeepsBeanSearcherDefault() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean("databaseDialect");
            assertThat(ctx).doesNotHaveBean("scaffoldDynamicDialectSupport");
            // BS 默认方言接管（MySQL）
            assertThat(ctx.getBean("dialect")).isInstanceOf(MySqlDialect.class);
        });
    }

    @Test
    void declaredDbTypeOverridesBeanSearcherConfig() {
        runner.withPropertyValues(
                        "scaffold.database.db-type=KINGBASE",
                        "bean-searcher.sql.dialect=MySQL")
                .run(ctx -> {
                    // 脚手架派生方言注册，BS 的 bean-searcher.sql.dialect 被覆盖
                    assertThat(ctx.getBean("databaseDialect")).isInstanceOf(PostgreSqlDialect.class);
                    assertThat(ctx).doesNotHaveBean("dialect");
                    // 动态方言支撑器同步注册
                    assertThat(ctx).hasBean("scaffoldDynamicDialectSupport");
                });
    }

    @Test
    void singleDbRegistersPlainDialect() {
        runner.withPropertyValues("scaffold.database.db-type=ORACLE")
                .run(ctx -> {
                    assertThat(ctx.getBean("databaseDialect"))
                            .isInstanceOf(cn.zhxu.bs.dialect.OracleDialect.class);
                });
    }

    @Test
    void multiDbAssemblesDynamicDialect() {
        runner.withPropertyValues(
                        "scaffold.database.db-type=MYSQL",
                        "scaffold.datasource.dynamic.datasources.cemetery.db-type=KINGBASE")
                .run(ctx -> {
                    cn.zhxu.bs.dialect.DynamicDialect dynamic =
                            (cn.zhxu.bs.dialect.DynamicDialect) ctx.getBean("databaseDialect");
                    assertThat(dynamic.getDialectMap().get("cemetery")).isInstanceOf(PostgreSqlDialect.class);
                    assertThat(dynamic.lookup()).isInstanceOf(MySqlDialect.class); // default = 全局
                });
    }

    @Test
    void userDialectBeanWins() {
        runner.withPropertyValues("scaffold.database.db-type=KINGBASE")
                .withBean("myDialect", MySqlDialect.class)
                .run(ctx -> {
                    // 用户 Bean 优先：脚手架与 BS 都退让
                    assertThat(ctx).doesNotHaveBean("databaseDialect");
                    assertThat(ctx.getBean("myDialect")).isInstanceOf(MySqlDialect.class);
                    assertThat(ctx).doesNotHaveBean("dialect");
                });
    }
}
