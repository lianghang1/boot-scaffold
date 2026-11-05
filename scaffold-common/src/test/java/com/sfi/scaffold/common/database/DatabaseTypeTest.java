package com.sfi.scaffold.common.database;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DatabaseType} 映射规则测试：方言组折叠 + Hibernate platform 派生 + MySQL 系判定。
 *
 * @author lianghang
 */
class DatabaseTypeTest {

    @Test
    void limitGroup() {
        assertThat(DatabaseType.MYSQL.paginationGroup()).isEqualTo(DatabaseType.PaginationGroup.LIMIT);
        assertThat(DatabaseType.POSTGRESQL.paginationGroup()).isEqualTo(DatabaseType.PaginationGroup.LIMIT);
        assertThat(DatabaseType.KINGBASE.paginationGroup()).isEqualTo(DatabaseType.PaginationGroup.LIMIT);
        assertThat(DatabaseType.GAUSS.paginationGroup()).isEqualTo(DatabaseType.PaginationGroup.LIMIT);
        assertThat(DatabaseType.OCEANBASE.paginationGroup()).isEqualTo(DatabaseType.PaginationGroup.LIMIT);
        assertThat(DatabaseType.UNKNOWN.paginationGroup()).isEqualTo(DatabaseType.PaginationGroup.LIMIT);
    }

    @Test
    void fetchGroup() {
        assertThat(DatabaseType.ORACLE.paginationGroup()).isEqualTo(DatabaseType.PaginationGroup.FETCH);
        assertThat(DatabaseType.SQLSERVER.paginationGroup()).isEqualTo(DatabaseType.PaginationGroup.FETCH);
        assertThat(DatabaseType.DAMENG.paginationGroup()).isEqualTo(DatabaseType.PaginationGroup.FETCH);
    }

    @Test
    void mysqlFamily() {
        assertThat(DatabaseType.MYSQL.isMySqlFamily()).isTrue();
        assertThat(DatabaseType.OCEANBASE.isMySqlFamily()).isTrue();
        assertThat(DatabaseType.KINGBASE.isMySqlFamily()).isFalse();
        assertThat(DatabaseType.DAMENG.isMySqlFamily()).isFalse();
        assertThat(DatabaseType.POSTGRESQL.isMySqlFamily()).isFalse();
    }

    @Test
    void hibernatePlatform() {
        // 国产库派生
        assertThat(DatabaseType.KINGBASE.hibernatePlatform()).isEqualTo("org.hibernate.dialect.PostgreSQL10Dialect");
        assertThat(DatabaseType.GAUSS.hibernatePlatform()).isEqualTo("org.hibernate.dialect.PostgreSQL10Dialect");
        assertThat(DatabaseType.OCEANBASE.hibernatePlatform()).isEqualTo("org.hibernate.dialect.MySQL57Dialect");
        assertThat(DatabaseType.DAMENG.hibernatePlatform()).isEqualTo("org.hibernate.dialect.DmDialect");
        // 主流四库交给 Hibernate 自识别
        assertThat(DatabaseType.MYSQL.hibernatePlatform()).isNull();
        assertThat(DatabaseType.POSTGRESQL.hibernatePlatform()).isNull();
        assertThat(DatabaseType.ORACLE.hibernatePlatform()).isNull();
        assertThat(DatabaseType.SQLSERVER.hibernatePlatform()).isNull();
    }
}
