package com.sfi.scaffold.query.config;

import com.sfi.scaffold.common.database.DatabaseType;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据库类型解析器（query 通路）。
 *
 * <p>启动期从 Environment 收集 db-type 声明（全局 {@code scaffold.database.db-type} +
 * 各数据源块内联 {@code scaffold.datasource.dynamic.datasources.<name>.db-type}），
 * 运行期供 doNativeQuery 分页与 @TableX.useIndex 校验按数据源解析类型。</p>
 *
 * <p>解析规则（与 data-jpa / Bean Searcher 通路一致）：
 * 块内声明 → 全局声明 → 默认 MYSQL（未声明时维持现状行为）。</p>
 *
 * @author lianghang
 */
public final class DatabaseTypes {

    private static volatile DatabaseType global;
    private static volatile DatabaseType primaryType;
    private static volatile Map<String, DatabaseType> byDataSource = Collections.emptyMap();
    private static volatile boolean declared;

    private DatabaseTypes() {
    }

    /** 由自动配置调用，应用内不要使用 */
    public static void configure(Environment env) {
        global = env.getProperty("scaffold.database.db-type", DatabaseType.class);
        Map<String, DatabaseType> ds = dataSourceTypes(env);
        byDataSource = Collections.unmodifiableMap(new LinkedHashMap<>(ds));
        primaryType = ds.get(primaryName(env));
        declared = global != null || !ds.isEmpty();
    }

    /** 由自动配置测试调用，应用内不要使用 */
    public static void reset() {
        global = null;
        primaryType = null;
        byDataSource = Collections.emptyMap();
        declared = false;
    }

    /**
     * 解析数据源对应的数据库类型：{@code dataSource=null} 表示默认库（单库 / 未切数据源）。
     */
    public static DatabaseType resolve(String dataSource) {
        if (dataSource != null) {
            DatabaseType type = byDataSource.get(dataSource);
            return type != null ? type : (global != null ? global : DatabaseType.MYSQL);
        }
        if (primaryType != null) {
            return primaryType;
        }
        return global != null ? global : DatabaseType.MYSQL;
    }

    /** 是否存在显式声明（决定 Bean Searcher 方言桥接 / JPA dialect 派生是否激活） */
    public static boolean declaredInEnvironment(Environment env) {
        if (env.getProperty("scaffold.database.db-type", DatabaseType.class) != null) {
            return true;
        }
        return !dataSourceTypes(env).isEmpty();
    }

    /** 全局 db-type（未声明返回 null） */
    public static DatabaseType globalType(Environment env) {
        return env.getProperty("scaffold.database.db-type", DatabaseType.class);
    }

    /** 动态数据源默认名（scaffold.datasource.dynamic.primary） */
    public static String primaryName(Environment env) {
        return env.getProperty("scaffold.datasource.dynamic.primary", "master");
    }

    /** 各数据源块内联声明的 db-type（key = 数据源名） */
    public static Map<String, DatabaseType> dataSourceTypes(Environment env) {
        Map<String, DbConf> confs = Binder.get(env)
                .bind("scaffold.datasource.dynamic.datasources", Bindable.mapOf(String.class, DbConf.class))
                .orElseGet(LinkedHashMap::new);
        LinkedHashMap<String, DatabaseType> types = new LinkedHashMap<>();
        confs.forEach((name, conf) -> {
            if (conf != null && conf.getDbType() != null) {
                types.put(name, conf.getDbType());
            }
        });
        return types;
    }

    /** 仅绑定 db-type 字段的轻量数据源配置（避免依赖 data-jpa 的属性类） */
    static class DbConf {

        private DatabaseType dbType;

        public DatabaseType getDbType() {
            return dbType;
        }

        public void setDbType(DatabaseType dbType) {
            this.dbType = dbType;
        }
    }

    /** Bean 注册条件：全局或任一数据源块内显式声明了 db-type */
    static class DeclaredCondition implements ConfigurationCondition {

        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return declaredInEnvironment(context.getEnvironment());
        }
    }
}
