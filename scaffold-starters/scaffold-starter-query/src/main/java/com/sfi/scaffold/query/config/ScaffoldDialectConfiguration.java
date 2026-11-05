package com.sfi.scaffold.query.config;

import cn.zhxu.bs.boot.BeanSearcherAutoConfiguration;
import cn.zhxu.bs.boot.NamedDataSource;
import cn.zhxu.bs.dialect.DaMengDialect;
import cn.zhxu.bs.dialect.Dialect;
import cn.zhxu.bs.dialect.DynamicDialect;
import cn.zhxu.bs.dialect.DynamicDialectSupport;
import cn.zhxu.bs.dialect.MySqlDialect;
import cn.zhxu.bs.dialect.OracleDialect;
import cn.zhxu.bs.dialect.PostgreSqlDialect;
import cn.zhxu.bs.dialect.SqlServerDialect;
import com.sfi.scaffold.common.database.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bean Searcher 方言桥接：把 {@code scaffold.database.db-type} 声明派生为 BS 的
 * {@link Dialect}，使 Bean Searcher 检索（含 @TableX 计划）随统一声明自动适配数据库。
 *
 * <p>覆盖机制（BS 官方设计）：BS 自动配置的 dialect Bean 带
 * {@code @ConditionalOnMissingBean(Dialect.class)}，本配置类
 * {@code before = BeanSearcherAutoConfiguration} 先评估，因此优先级为——
 * 用户自定义 Dialect Bean &gt; 脚手架按 db-type 派生 &gt; BS 默认（{@code bean-searcher.sql.dialect}，MySQL）。
 * 未声明 db-type 时本配置整体不激活，BS 行为与现状一致。</p>
 *
 * <p>多库场景组装 {@link DynamicDialect}：default 取默认库类型，每个具名数据源
 * （dynamic 块声明 + 宿主 {@link NamedDataSource} Bean 名）put 对应方言；
 * {@link DynamicDialectSupport} 由 BS 内核在检索前后设置/清理当前数据源名，
 * 必须同步注册（BS 仅在 {@code dialect-dynamic=true} 时注册，此处不依赖该配置项）。</p>
 *
 * @author lianghang
 */
@AutoConfiguration(before = BeanSearcherAutoConfiguration.class)
public class ScaffoldDialectConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldDialectConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(Dialect.class)
    @Conditional(DatabaseTypes.DeclaredCondition.class)
    public Dialect databaseDialect(Environment env, ObjectProvider<List<NamedDataSource>> namedDataSources) {
        if (env.containsProperty("bean-searcher.sql.dialect")) {
            log.warn("已声明 scaffold.database.db-type，bean-searcher.sql.dialect 配置将被忽略（以脚手架统一声明为准）");
        }
        DatabaseType global = DatabaseTypes.globalType(env);
        Map<String, DatabaseType> dsTypes = DatabaseTypes.dataSourceTypes(env);
        DatabaseType defaultType = defaultType(env, global, dsTypes);

        // 具名数据源全集：dynamic 块内联声明的名字 + 宿主 NamedDataSource Bean 名
        Set<String> names = new LinkedHashSet<>(dsTypes.keySet());
        List<NamedDataSource> ndsList = namedDataSources.getIfAvailable();
        if (ndsList != null) {
            for (NamedDataSource nds : ndsList) {
                names.add(nds.getName());
            }
        }
        if (names.isEmpty()) {
            return toBsDialect(defaultType);
        }
        DynamicDialect dynamic = new DynamicDialect();
        dynamic.setDefaultDialect(toBsDialect(defaultType));
        for (String name : names) {
            DatabaseType type = dsTypes.containsKey(name)
                    ? dsTypes.get(name)
                    : (global != null ? global : DatabaseType.MYSQL);
            dynamic.put(name, toBsDialect(type));
        }
        return dynamic;
    }

    /**
     * 动态方言的数据源名设置/清理器：BS 只在 {@code bean-searcher.sql.dialect-dynamic=true}
     * 时注册它，脚手架自组装 DynamicDialect 时必须同步注册，否则方言永远落在 default 上。
     */
    @Bean
    @ConditionalOnMissingBean(DynamicDialectSupport.class)
    @Order(Ordered.LOWEST_PRECEDENCE / 2)
    @Conditional(DatabaseTypes.DeclaredCondition.class)
    public DynamicDialectSupport scaffoldDynamicDialectSupport() {
        return new DynamicDialectSupport();
    }

    /** 默认库类型：primary 块内声明 → 全局 db-type → MYSQL */
    private static DatabaseType defaultType(Environment env, DatabaseType global, Map<String, DatabaseType> dsTypes) {
        DatabaseType primary = dsTypes.get(DatabaseTypes.primaryName(env));
        return primary != null ? primary : (global != null ? global : DatabaseType.MYSQL);
    }

    /** DatabaseType → Bean Searcher 内置方言实例 */
    private static Dialect toBsDialect(DatabaseType type) {
        switch (type) {
            case ORACLE:
                return new OracleDialect();
            case SQLSERVER:
                return new SqlServerDialect();
            case DAMENG:
                return new DaMengDialect();
            case POSTGRESQL:
            case KINGBASE:
            case GAUSS:
                return new PostgreSqlDialect();
            case MYSQL:
            case OCEANBASE:
            case UNKNOWN:
            default:
                return new MySqlDialect();
        }
    }
}
