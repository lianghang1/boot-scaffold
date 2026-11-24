package com.sfi.scaffold.starter.data.jpa.config;

import com.sfi.scaffold.starter.data.jpa.audit.ScaffoldAuditorHolder;
import com.sfi.scaffold.starter.data.jpa.datachange.DataChangeEventListener;
import com.sfi.scaffold.starter.data.jpa.datascope.DataScopeAspect;
import com.sfi.scaffold.starter.data.jpa.id.Snowflake;
import com.sfi.scaffold.starter.data.jpa.id.SnowflakeIdentifierGenerator;
import com.sfi.scaffold.starter.data.jpa.logicdelete.FindByIdDeletedAspect;
import com.sfi.scaffold.starter.data.jpa.logicdelete.JpaDeletedFilterAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;
import java.util.Optional;

/**
 * starter-data-jpa 自动装配器，提供 JPA Auditing 与雪花 ID 单例。
 *
 * <p>审计人通过 {@code AuditorAware} 从 {@code ScaffoldAuditorHolder} 读取，
 * 由安全层在认证后写入，data-jpa 自身不感知登录态；实体与 Repository 扫描
 * 范围统一为 {@code com.sfi.scaffold}，业务模块放各自包下即可被纳入。</p>
 *
 * <p>雪花 ID 以单例 Bean 构建并同步初始化到 {@code SnowflakeIdentifierGenerator}
 * 的静态引用——Hibernate 通过反射实例化生成器，拿不到 Spring 容器，只能走静态注入。</p>
 *
 * @author lianghang
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "scaffoldAuditorAware")
@EntityScan(basePackages = "com.sfi.scaffold")
@EnableJpaRepositories(basePackages = "com.sfi.scaffold")
public class DataJpaAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "scaffold.data")
    public SnowflakeProperties snowflakeProperties() {
        return new SnowflakeProperties();
    }

    @Bean
    public Snowflake snowflake(SnowflakeProperties properties) {
        Snowflake snowflake = new Snowflake(properties.getWorkerId(), properties.getDatacenterId());
        SnowflakeIdentifierGenerator.init(snowflake);
        return snowflake;
    }

    @Bean
    @ConditionalOnMissingBean(AuditorAware.class)
    public AuditorAware<String> scaffoldAuditorAware() {
        return () -> Optional.ofNullable(ScaffoldAuditorHolder.get());
    }

    @Bean
    @ConditionalOnMissingBean(DataScopeAspect.class)
    public DataScopeAspect dataScopeAspect(PlatformTransactionManager transactionManager) {
        return new DataScopeAspect(transactionManager);
    }

    /**
     * 逻辑删除 jpa 通路切面：Filter 白名单式过滤 + findById 结果兜底，
     * 与 query 通路的 DeletedParamFilter 共同实现两通路口径一致。
     * 需逻辑删除的实体自行声明 deleted 字段并标注 @Filter 即自动生效。
     */
    @Bean
    @ConditionalOnMissingBean(JpaDeletedFilterAspect.class)
    public JpaDeletedFilterAspect jpaDeletedFilterAspect(PlatformTransactionManager transactionManager) {
        return new JpaDeletedFilterAspect(transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(FindByIdDeletedAspect.class)
    public FindByIdDeletedAspect findByIdDeletedAspect() {
        return new FindByIdDeletedAspect();
    }

    /**
     * 数据变更留痕监听器：注册 Hibernate POST_COMMIT 事件组，
     * 对 {@code @DataChange} 实体在事务提交后发布变更事件。
     * {@code scaffold.data.data-change-enabled=false} 可整体关闭。
     */
    @Bean
    @ConditionalOnProperty(prefix = "scaffold.data", name = "data-change-enabled",
            havingValue = "true", matchIfMissing = true)
    public DataChangeEventListener dataChangeEventListener(EntityManagerFactory entityManagerFactory) {
        return new DataChangeEventListener(entityManagerFactory);
    }

    /**
     * Hibernate dialect 派生：用户未显式配置 platform 时，按统一声明
     * （{@code scaffold.database.db-type} / 数据源块内 {@code db-type}）派生注入。
     */
    @Bean
    public static HibernateDialectPostProcessor hibernateDialectPostProcessor() {
        return new HibernateDialectPostProcessor();
    }
}
