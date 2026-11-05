package com.sfi.scaffold.starter.data.jpa.config;

import com.sfi.scaffold.common.database.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Collections;

/**
 * Hibernate dialect 派生器：用户未显式配置 {@code spring.jpa.database-platform} /
 * {@code spring.jpa.properties.hibernate.dialect} 时，按统一声明
 * （primary 数据源块内 {@code db-type} → 全局 {@code scaffold.database.db-type}）
 * 派生 Hibernate dialect 注入 Environment；主流数据库返回 null，维持 Hibernate 自识别。
 *
 * <p>显式配置永远优先——本类只在用户未配置时补充，不覆盖任何已有声明。
 * 达梦依赖社区方言 jar，classpath 探测缺失时告警跳过，不引入依赖。</p>
 *
 * @author lianghang
 */
public class HibernateDialectPostProcessor implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(HibernateDialectPostProcessor.class);

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ConfigurableEnvironment env = beanFactory.getBean(ConfigurableEnvironment.class);
        applyDialect(env);
    }

    /** 核心逻辑抽静态便于单测直调。 */
    static void applyDialect(ConfigurableEnvironment env) {
        // 显式配置永远优先
        if (env.getProperty("spring.jpa.database-platform") != null
                || env.getProperty("spring.jpa.properties.hibernate.dialect") != null) {
            return;
        }
        DatabaseType type = primaryType(env);
        if (type == null) {
            return; // 未声明 db-type，维持 Hibernate 自识别（现状不变）
        }
        String platform = type.hibernatePlatform();
        if (platform == null) {
            return; // 主流数据库，交给 Hibernate 自识别
        }
        if (type == DatabaseType.DAMENG && !classPresent(platform)) {
            log.warn("scaffold.database.db-type=DAMENG：classpath 未找到社区 Hibernate 方言 {}，"
                    + "请引入达梦方言依赖，或显式配置 spring.jpa.database-platform，本次不注入", platform);
            return;
        }
        env.getPropertySources().addLast(new MapPropertySource("scaffoldDbDialect",
                Collections.singletonMap("spring.jpa.properties.hibernate.dialect", platform)));
        log.info("按统一声明派生 Hibernate dialect：db-type={} -> {}", type, platform);
    }

    /**
     * 默认库（JPA 通路）的类型：单库取全局 db-type；多库取 primary 块内 db-type，缺省回落全局。
     */
    private static DatabaseType primaryType(ConfigurableEnvironment env) {
        DatabaseType global = env.getProperty("scaffold.database.db-type", DatabaseType.class);
        try {
            DynamicDataSourceProperties dynamic = Binder.get(env)
                    .bind("scaffold.datasource.dynamic", Bindable.of(DynamicDataSourceProperties.class))
                    .orElse(null);
            if (dynamic != null && dynamic.getDatasources() != null) {
                DynamicDataSourceProperties.Conf conf = dynamic.getDatasources().get(dynamic.getPrimary());
                if (conf != null && conf.getDbType() != null) {
                    return conf.getDbType();
                }
            }
        } catch (Exception e) {
            // dynamic 配置不完整时忽略，回落全局声明
        }
        return global;
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, HibernateDialectPostProcessor.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
