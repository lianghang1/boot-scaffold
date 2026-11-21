package com.sfi.scaffold.starter.tenant.config;

import cn.zhxu.bs.ParamFilter;
import com.sfi.scaffold.starter.tenant.aspect.FindByIdTenantAspect;
import com.sfi.scaffold.starter.tenant.aspect.JpaTenantFilterAspect;
import com.sfi.scaffold.starter.tenant.aspect.TenantIgnoreAspect;
import com.sfi.scaffold.starter.tenant.bs.DataScopeParamFilter;
import com.sfi.scaffold.starter.tenant.bs.TenantParamFilter;
import org.hibernate.Session;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 多租户自动装配（opt-in：{@code scaffold.tenant.enabled=true} 才生效）。
 *
 * <p>豁免切面恒装配；两条过滤通路按 classpath 条件装配——query 在场装配
 * BS ParamFilter，data-jpa 在场装配 Hibernate Filter 与 findById 兜底切面，
 * 缺席各自静默跳过。</p>
 *
 * @author lianghang
 */
@AutoConfiguration(afterName = {
        "com.sfi.scaffold.starter.data.jpa.config.DataJpaAutoConfiguration",
        "com.sfi.scaffold.query.config.QueryAutoConfiguration"})
@ConditionalOnProperty(name = "scaffold.tenant.enabled", havingValue = "true")
@EnableConfigurationProperties(TenantProperties.class)
public class TenantAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantIgnoreAspect tenantIgnoreAspect() {
        return new TenantIgnoreAspect();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ParamFilter.class)
    static class BeanSearcherTenantConfiguration {

        @Bean
        @ConditionalOnMissingBean(TenantParamFilter.class)
        public TenantParamFilter tenantParamFilter(TenantProperties properties) {
            return new TenantParamFilter(properties);
        }

        /** 数据权限检索过滤器：与 jpa 通路共用 DataScopeHolder 决策。 */
        @Bean
        @ConditionalOnMissingBean(DataScopeParamFilter.class)
        public DataScopeParamFilter dataScopeParamFilter() {
            return new DataScopeParamFilter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({Session.class, JpaRepository.class})
    static class JpaTenantConfiguration {

        @Bean
        @ConditionalOnMissingBean(JpaTenantFilterAspect.class)
        public JpaTenantFilterAspect jpaTenantFilterAspect(PlatformTransactionManager transactionManager) {
            return new JpaTenantFilterAspect(transactionManager);
        }

        @Bean
        @ConditionalOnMissingBean(FindByIdTenantAspect.class)
        public FindByIdTenantAspect findByIdTenantAspect() {
            return new FindByIdTenantAspect();
        }
    }
}
