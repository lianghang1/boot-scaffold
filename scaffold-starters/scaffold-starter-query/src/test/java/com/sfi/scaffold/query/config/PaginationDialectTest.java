package com.sfi.scaffold.query.config;

import com.sfi.scaffold.common.database.DatabaseType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PaginationDialect} 两组分页片段 + {@link DatabaseTypes} 解析链测试。
 *
 * <p>解析链：数据源块内 db-type → 全局 scaffold.database.db-type → MYSQL（未声明维持现状）。</p>
 *
 * @author lianghang
 */
class PaginationDialectTest {

    @AfterEach
    void cleanUp() {
        DatabaseTypes.reset();
    }

    // ==================== PaginationDialect 纯函数 ====================

    @Test
    void limitGroupPaginate() {
        String sql = PaginationDialect.paginate(DatabaseType.MYSQL, "select * from t");
        assertThat(sql).isEqualTo("select * from t LIMIT :__limit OFFSET :__offset");
        assertThat(PaginationDialect.paginate(DatabaseType.KINGBASE, "select 1"))
                .isEqualTo("select 1 LIMIT :__limit OFFSET :__offset");
    }

    @Test
    void fetchGroupPaginate() {
        assertThat(PaginationDialect.paginate(DatabaseType.DAMENG, "select * from t"))
                .isEqualTo("select * from t OFFSET :__offset ROWS FETCH NEXT :__limit ROWS ONLY");
        assertThat(PaginationDialect.paginate(DatabaseType.ORACLE, "select 1"))
                .isEqualTo("select 1 OFFSET :__offset ROWS FETCH NEXT :__limit ROWS ONLY");
    }

    @Test
    void capFragment() {
        assertThat(PaginationDialect.cap(DatabaseType.MYSQL, "select * from t"))
                .isEqualTo("select * from t LIMIT :__limit");
        assertThat(PaginationDialect.cap(DatabaseType.DAMENG, "select * from t"))
                .isEqualTo("select * from t FETCH FIRST :__limit ROWS ONLY");
    }

    // ==================== DatabaseTypes 解析链 ====================

    @Test
    void resolveWithoutDeclarationFallsBackToMysql() {
        DatabaseTypes.configure(new MockEnvironment());
        assertThat(DatabaseTypes.resolve(null)).isEqualTo(DatabaseType.MYSQL);
        assertThat(DatabaseTypes.resolve("any")).isEqualTo(DatabaseType.MYSQL);
        assertThat(DatabaseTypes.declaredInEnvironment(new MockEnvironment())).isFalse();
    }

    @Test
    void globalDeclaration() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.database.db-type", "KINGBASE");
        DatabaseTypes.configure(env);
        assertThat(DatabaseTypes.resolve(null)).isEqualTo(DatabaseType.KINGBASE);
        // 具名未覆盖的数据源回落全局
        assertThat(DatabaseTypes.resolve("legacy")).isEqualTo(DatabaseType.KINGBASE);
        assertThat(DatabaseTypes.declaredInEnvironment(env)).isTrue();
    }

    @Test
    void perDataSourceDeclaration() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.database.db-type", "MYSQL");
        env.setProperty("scaffold.datasource.dynamic.datasources.cemetery.db-type", "KINGBASE");
        env.setProperty("scaffold.datasource.dynamic.datasources.archive.db-type", "DAMENG");
        DatabaseTypes.configure(env);
        assertThat(DatabaseTypes.resolve("cemetery")).isEqualTo(DatabaseType.KINGBASE);
        assertThat(DatabaseTypes.resolve("archive")).isEqualTo(DatabaseType.DAMENG);
        // 未覆盖数据源回落全局
        assertThat(DatabaseTypes.resolve("other")).isEqualTo(DatabaseType.MYSQL);
        // 默认库：primary（master）无内联声明 → 全局
        assertThat(DatabaseTypes.resolve(null)).isEqualTo(DatabaseType.MYSQL);
    }

    @Test
    void primaryDataSourceDeclarationWinsForDefault() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.datasource.dynamic.primary", "cemetery");
        env.setProperty("scaffold.datasource.dynamic.datasources.cemetery.db-type", "KINGBASE");
        DatabaseTypes.configure(env);
        assertThat(DatabaseTypes.resolve(null)).isEqualTo(DatabaseType.KINGBASE);
    }

    @Test
    void declaredOnlyWhenExplicit() {
        // dynamic 有数据源连接但没声明 db-type：不算声明
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.datasource.dynamic.datasources.master.url", "jdbc:mysql://localhost/db");
        assertThat(DatabaseTypes.declaredInEnvironment(env)).isFalse();
        assertThat(DatabaseTypes.dataSourceTypes(env)).isEmpty();
    }
}
