package com.sfi.scaffold.starter.security.token.config;

import com.sfi.scaffold.common.metrics.CounterMetrics;
import com.sfi.scaffold.starter.security.security.ScaffoldSecurityCustomizer;
import com.sfi.scaffold.starter.security.token.RedisTokenService;
import com.sfi.scaffold.starter.security.token.TokenService;
import com.sfi.scaffold.starter.security.token.filter.TokenAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * token 认证组件装配：token 会话 / 登录防刷 / 图形验证码 / 链挂载。
 *
 * <p>Redis 是本组件的硬前置：类路径无 StringRedisTemplate 时整体跳过装配
 * （边界链仍 fail-closed，认证能力缺席即全关）。无会话管控诉求的项目
 * 不引本组件即可。</p>
 *
 * @author lianghang
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(TokenProperties.class)
public class TokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TokenService.class)
    public TokenService tokenService(StringRedisTemplate redisTemplate,
                                     com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                     TokenProperties properties) {
        return new RedisTokenService(redisTemplate, objectMapper,
                properties.getTtlSeconds(), properties.getRenewThresholdPercent());
    }

    /** 登录失败锁定：账密登录在 service 层调用（记录/判定/清零），账号 + IP 双维度 */
    @Bean
    @ConditionalOnMissingBean(com.sfi.scaffold.starter.security.policy.LoginFailureGuard.class)
    public com.sfi.scaffold.starter.security.policy.LoginFailureGuard loginFailureGuard(
            StringRedisTemplate redisTemplate,
            com.sfi.scaffold.starter.security.config.SecurityProperties securityProperties,
            TokenProperties tokenProperties,
            ObjectProvider<CounterMetrics> counterMetrics) {
        return new com.sfi.scaffold.starter.security.policy.LoginFailureGuard(
                redisTemplate, securityProperties.getPasswordPolicy(),
                tokenProperties.getIpGuard(), counterMetrics.getIfAvailable());
    }

    /** 图形验证码（Hutool + Redis 一次性），登录防机器撞库 */
    @Bean
    @ConditionalOnMissingBean(com.sfi.scaffold.starter.security.captcha.CaptchaService.class)
    public com.sfi.scaffold.starter.security.captcha.CaptchaService captchaService(
            StringRedisTemplate redisTemplate, TokenProperties properties) {
        return new com.sfi.scaffold.starter.security.captcha.CaptchaService(
                redisTemplate, properties.getCaptcha());
    }

    /** 认证过滤器经 Customizer 挂进边界链（链内匿名/白名单路径不受影响）。 */
    @Bean
    public ScaffoldSecurityCustomizer tokenAuthenticationCustomizer(TokenService tokenService) {
        return new ScaffoldSecurityCustomizer() {
            @Override
            public void customize(HttpSecurity http) throws Exception {
                http.addFilterBefore(
                        new TokenAuthenticationFilter(tokenService),
                        UsernamePasswordAuthenticationFilter.class);
            }
        };
    }

    /** micrometer 在场时绑定真实打点；嵌套类经 ASM 元数据判定条件，类加载安全 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MicrometerCounterMetricsConfiguration {

        @Bean
        CounterMetrics micrometerCounterMetrics(ObjectProvider<io.micrometer.core.instrument.MeterRegistry> registry) {
            return new com.sfi.scaffold.common.metrics.MicrometerCounterMetrics(registry.getIfAvailable());
        }
    }

    /** 兜底空实现：micrometer 不在场时静默 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(CounterMetrics.class)
    static class DefaultCounterMetricsConfiguration {

        @Bean
        CounterMetrics noopCounterMetrics() {
            return CounterMetrics.NOOP;
        }
    }
}
