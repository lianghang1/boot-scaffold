package com.sfi.scaffold.starter.liquibase.config;

import com.sfi.scaffold.common.database.DatabaseType;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 从 Environment 读取数据库类型声明（与 query 通路同一条解析链的私有副本，
 * 避免 liquibase starter 依赖 query/data-jpa starter）：
 * <pre>
 *   数据源块的 db-type（scaffold.datasource.dynamic.datasources.&lt;name&gt;.db-type）
 *     → 全局 scaffold.database.db-type
 *     → 不声明（主流库由 Liquibase 官方自识别）
 * </pre>
 * 同时读取数据源块内联的 liquibase 配置（{@code datasources.<name>.liquibase.change-log}）：
 * 键位结构由 data-jpa 的 Conf.Liquibase 声明（供 IDE 校验），运行时消费归本 starter。
 */
public final class LiquibaseDatabaseDeclarations {

    private static final String PROP_GLOBAL_TYPE = "scaffold.database.db-type";
    private static final String PROP_DATASOURCES = "scaffold.datasource.dynamic.datasources";

    private LiquibaseDatabaseDeclarations() {
    }

    /** 全局默认类型；未声明返回 null。 */
    public static DatabaseType globalType(Environment env) {
        return Binder.get(env).bind(PROP_GLOBAL_TYPE, DatabaseType.class).orElse(null);
    }

    /** 各数据源块内声明的类型（仅包含写了 db-type 的块）。 */
    public static Map<String, DatabaseType> dataSourceTypes(Environment env) {
        Map<String, DbConf> conf = bindDatasources(env);
        Map<String, DatabaseType> result = new LinkedHashMap<>();
        conf.forEach((name, c) -> {
            if (c != null && c.dbType != null) {
                result.put(name, c.dbType);
            }
        });
        return result;
    }

    /** 所有已声明的类型（全局 + 各块，去重）。 */
    public static Set<DatabaseType> declaredTypes(Environment env) {
        Set<DatabaseType> types = new LinkedHashSet<>();
        DatabaseType global = globalType(env);
        if (global != null) {
            types.add(global);
        }
        types.addAll(dataSourceTypes(env).values());
        return types;
    }

    /** 数据源名 → 内联 change-log（仅包含配置了 liquibase.change-log 的数据源）。 */
    public static Map<String, String> changelogs(Environment env) {
        Map<String, DbConf> conf = bindDatasources(env);
        Map<String, String> result = new LinkedHashMap<>();
        conf.forEach((name, c) -> {
            if (c != null && c.liquibase != null && c.liquibase.changeLog != null) {
                result.put(name, c.liquibase.changeLog);
            }
        });
        return result;
    }

    private static Map<String, DbConf> bindDatasources(Environment env) {
        return Binder.get(env)
                .bind(PROP_DATASOURCES, Bindable.mapOf(String.class, DbConf.class))
                .orElse(Collections.emptyMap());
    }

    /** 数据源块的宽松映射目标：只关心 db-type 与 liquibase 子键，其余字段由 Binder 忽略。 */
    static class DbConf {
        private DatabaseType dbType;
        private LiquibaseConf liquibase;

        public DatabaseType getDbType() {
            return dbType;
        }

        public void setDbType(DatabaseType dbType) {
            this.dbType = dbType;
        }

        public LiquibaseConf getLiquibase() {
            return liquibase;
        }

        public void setLiquibase(LiquibaseConf liquibase) {
            this.liquibase = liquibase;
        }
    }

    static class LiquibaseConf {
        private String changeLog;

        public String getChangeLog() {
            return changeLog;
        }

        public void setChangeLog(String changeLog) {
            this.changeLog = changeLog;
        }
    }
}
