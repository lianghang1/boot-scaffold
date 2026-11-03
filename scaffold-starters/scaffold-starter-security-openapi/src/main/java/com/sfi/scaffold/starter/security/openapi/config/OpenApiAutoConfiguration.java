package com.sfi.scaffold.starter.security.openapi.config;

import com.sfi.scaffold.starter.security.openapi.AppSecretProvider;
import com.sfi.scaffold.starter.security.openapi.OpenApiSignFilter;
import com.sfi.scaffold.starter.security.security.ScaffoldSecurityCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * openapi 签名认证组件装配：HMAC 签名过滤器经 Customizer 挂进边界链。
 *
 * <p>fail closed：业务方未提供 {@link AppSecretProvider} Bean 时全部拒绝。
 * Redis 为可选增强——在场时 nonce 防重放（一次性），缺席时退化为时间戳窗口防护。</p>
 *
 * @author lianghang
 */
@AutoConfiguration
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiAutoConfiguration {

    /** fail closed：业务方未提供凭证提供者时，签名认证全部拒绝 */
    @Bean
    @ConditionalOnMissingBean(AppSecretProvider.class)
    public AppSecretProvider denyAllAppSecretProvider() {
        return appKey -> null;
    }

    @Bean
    public OpenApiSignFilter openApiSignFilter(AppSecretProvider appSecretProvider,
                                               OpenApiProperties properties,
                                               ObjectProvider<StringRedisTemplate> redisTemplate) {
        return new OpenApiSignFilter(appSecretProvider, properties, redisTemplate.getIfAvailable());
    }

    @Bean
    public ScaffoldSecurityCustomizer openApiSignCustomizer(OpenApiSignFilter signFilter) {
        return new ScaffoldSecurityCustomizer() {
            @Override
            public void customize(HttpSecurity http) throws Exception {
                http.addFilterBefore(signFilter, UsernamePasswordAuthenticationFilter.class);
            }
        };
    }
}
