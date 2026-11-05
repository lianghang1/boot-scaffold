package com.sfi.scaffold.starter.data.jpa.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HibernateDialectPostProcessor} 派生规则测试：
 * 显式配置永远优先；未声明不动；国产库派生；达梦缺社区方言时跳过。
 *
 * @author lianghang
 */
class HibernateDialectPostProcessorTest {

    private static final String DIALECT_KEY = "spring.jpa.properties.hibernate.dialect";

    @Test
    void explicitPlatformAlwaysWins() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.Oracle12cDialect");
        env.setProperty("scaffold.database.db-type", "KINGBASE");
        HibernateDialectPostProcessor.applyDialect(env);
        assertThat(env.getProperty(DIALECT_KEY)).isNull();
    }

    @Test
    void noDeclarationKeepsAutoDetection() {
        MockEnvironment env = new MockEnvironment();
        HibernateDialectPostProcessor.applyDialect(env);
        assertThat(env.getProperty(DIALECT_KEY)).isNull();
    }

    @Test
    void mainStreamDbNotInjected() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.database.db-type", "MYSQL");
        HibernateDialectPostProcessor.applyDialect(env);
        assertThat(env.getProperty(DIALECT_KEY)).isNull();
    }

    @Test
    void kingbaseDerivesPostgreSQL10Dialect() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.database.db-type", "KINGBASE");
        HibernateDialectPostProcessor.applyDialect(env);
        assertThat(env.getProperty(DIALECT_KEY)).isEqualTo("org.hibernate.dialect.PostgreSQL10Dialect");
    }

    @Test
    void primaryDataSourceDbTypeWinsForJpa() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.datasource.dynamic.primary", "cemetery");
        env.setProperty("scaffold.datasource.dynamic.datasources.master.db-type", "MYSQL");
        env.setProperty("scaffold.datasource.dynamic.datasources.cemetery.db-type", "GAUSS");
        HibernateDialectPostProcessor.applyDialect(env);
        assertThat(env.getProperty(DIALECT_KEY)).isEqualTo("org.hibernate.dialect.PostgreSQL10Dialect");
    }

    @Test
    void damengSkippedWhenCommunityDialectMissing() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.database.db-type", "DAMENG");
        HibernateDialectPostProcessor.applyDialect(env);
        // 测试 classpath 无达梦方言 jar：告警跳过，不注入
        assertThat(env.getProperty(DIALECT_KEY)).isNull();
    }

    @Test
    void explicitHibernateDialectKeyAlsoWins() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(DIALECT_KEY, "org.hibernate.dialect.MySQL57Dialect");
        env.setProperty("scaffold.database.db-type", "KINGBASE");
        HibernateDialectPostProcessor.applyDialect(env);
        assertThat(env.getProperty(DIALECT_KEY)).isEqualTo("org.hibernate.dialect.MySQL57Dialect");
    }
}
