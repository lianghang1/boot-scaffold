package com.sfi.scaffold.starter.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfi.scaffold.common.exception.GlobalErrorCode;
import com.sfi.scaffold.starter.security.filter.InternalAuthenticationFilter;
import com.sfi.scaffold.starter.security.filter.OnlineTokenFilter;
import com.sfi.scaffold.starter.security.filter.OpenApiSignFilter;
import com.sfi.scaffold.starter.security.openapi.AppSecretProvider;
import com.sfi.scaffold.starter.security.token.RedisTokenService;
import com.sfi.scaffold.starter.security.token.TokenService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * 三入口安全体系的自动装配类，一条链一个配置方法，白名单唯一出自 SecurityProperties。
 *
 * <p>链路按 Order 分层：@Order(1) /openapi/** 走 HMAC 签名认证（APP 账户）；
 * @Order(2) /internal/** 走 token 会话加 RBAC 方法鉴权（OPERATOR）；
 * @Order(3) 其余请求归 online 链——permitAll 加可选 token（MEMBER）。互不影响、各司其职。</p>
 *
 * <p>Redis 为安全体系的可选依赖：token 会话、登录防刷、图形验证码、openapi 防重放
 * 均基于 Redis 存储。类路径无 Redis 时 {@link RedisBackedConfiguration} 整体跳过，
 * 由 {@link NoRedisDegradedConfiguration} 注册放行链兜底（避免 Spring Boot 默认
 * basic 认证接管全部接口），业务侧接入安全能力时须自行引入 spring-boot-starter-data-redis。</p>
 *
 * @author lianghang
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class ScaffoldSecurityAutoConfiguration {

    // ---------------- 公共设施（与 Redis 无关） ----------------

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** @ApiEncrypt 接口加解密：注解驱动，未标注接口零影响；密文经 DataCipher（starter-crypto 装配） */
    @Bean
    public com.sfi.scaffold.starter.security.apiencrypt.ApiEncryptRequestBodyAdvice apiEncryptRequestBodyAdvice() {
        return new com.sfi.scaffold.starter.security.apiencrypt.ApiEncryptRequestBodyAdvice();
    }

    @Bean
    public com.sfi.scaffold.starter.security.apiencrypt.ApiEncryptResponseBodyAdvice apiEncryptResponseBodyAdvice(
            ObjectMapper objectMapper) {
        return new com.sfi.scaffold.starter.security.apiencrypt.ApiEncryptResponseBodyAdvice(objectMapper);
    }

    /** 权限校验拦截器：对声明 {@code @RequirePermission} 的管理接口做 any-of 校验，
     * 无注解接口零开销直通。执行于认证过滤器之后，LoginContext 已就绪。 */
    @Bean
    @ConditionalOnMissingBean(com.sfi.scaffold.starter.security.permission.PermissionInterceptor.class)
    public org.springframework.web.servlet.HandlerInterceptor permissionInterceptor() {
        return new com.sfi.scaffold.starter.security.permission.PermissionInterceptor();
    }

    @Bean
    public org.springframework.web.servlet.config.annotation.WebMvcConfigurer permissionInterceptorConfigurer(
            org.springframework.web.servlet.HandlerInterceptor permissionInterceptor) {
        return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
            @Override
            public void addInterceptors(
                    org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
                registry.addInterceptor(permissionInterceptor).addPathPatterns("/**");
            }
        };
    }

    /**
     * Redis 在场时的全量安全装配：token 会话、登录防刷、图形验证码与三条安全链。
     * 嵌套类经 ASM 元数据判定条件，Redis 不在类路径时本类不加载，类引用安全。
     */
    @Configuration
    @EnableWebSecurity
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    @ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
    static class RedisBackedConfiguration {

        @Bean
        @ConditionalOnMissingBean(TokenService.class)
        public TokenService tokenService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                         SecurityProperties properties) {
            return new RedisTokenService(redisTemplate, objectMapper,
                    properties.getTokenTtlSeconds(), properties.getRenewThresholdPercent());
        }

        /** 登录失败锁定：internal 账密登录在 service 层调用（记录/判定/清零），账号 + IP 双维度 */
        @Bean
        @ConditionalOnMissingBean(com.sfi.scaffold.starter.security.policy.LoginFailureGuard.class)
        public com.sfi.scaffold.starter.security.policy.LoginFailureGuard loginFailureGuard(
                StringRedisTemplate redisTemplate, SecurityProperties properties,
                com.sfi.scaffold.common.metrics.CounterMetrics counterMetrics) {
            return new com.sfi.scaffold.starter.security.policy.LoginFailureGuard(
                    redisTemplate, properties.getPasswordPolicy(), properties.getIpGuard(), counterMetrics);
        }

        /** 图形验证码（Hutool + Redis 一次性），internal 登录防机器撞库 */
        @Bean
        @ConditionalOnMissingBean(com.sfi.scaffold.starter.security.captcha.CaptchaService.class)
        public com.sfi.scaffold.starter.security.captcha.CaptchaService captchaService(
                StringRedisTemplate redisTemplate, SecurityProperties properties) {
            return new com.sfi.scaffold.starter.security.captcha.CaptchaService(
                    redisTemplate, properties.getCaptcha());
        }

        // ---------------- 链①：/openapi/** ----------------

        @Bean
        @org.springframework.core.annotation.Order(1)
        public SecurityFilterChain openApiChain(HttpSecurity http,
                                                ObjectProvider<AppSecretProvider> provider,
                                                SecurityProperties properties,
                                                StringRedisTemplate redisTemplate) throws Exception {
            OpenApiSignFilter signFilter = new OpenApiSignFilter(
                    provider.getIfAvailable() != null ? provider.getIfAvailable() : denyAllProvider(),
                    properties, redisTemplate);
            http.antMatcher("/openapi/**")
                    .csrf().disable()
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .exceptionHandling().authenticationEntryPoint(
                            (request, response, e) -> write(response, GlobalErrorCode.UNAUTHORIZED))
                    .and()
                    .authorizeRequests().anyRequest().authenticated()
                    .and()
                    .addFilterBefore(signFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }

        /** fail closed：业务方未提供 AppSecretProvider 时，openapi 全部拒绝 */
        private AppSecretProvider denyAllProvider() {
            return appKey -> null;
        }

        // ---------------- 链②：/internal/** ----------------

        @Bean
        @org.springframework.core.annotation.Order(2)
        public SecurityFilterChain internalChain(HttpSecurity http, TokenService tokenService,
                                                 SecurityProperties properties) throws Exception {
            String[] whitelist = properties.getInternalWhitelist().toArray(new String[0]);
            http.antMatcher("/internal/**")
                    .csrf().disable()
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .authorizeRequests()
                    .antMatchers(whitelist).permitAll()
                    .anyRequest().authenticated()
                    .and()
                    .exceptionHandling()
                    .authenticationEntryPoint((request, response, e) -> write(response, GlobalErrorCode.UNAUTHORIZED))
                    .accessDeniedHandler((request, response, e) -> write(response, GlobalErrorCode.FORBIDDEN))
                    .and()
                    .addFilterBefore(new InternalAuthenticationFilter(tokenService, properties),
                            UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }

        // ---------------- 链③：online（默认 permitAll） ----------------

        @Bean
        @org.springframework.core.annotation.Order(3)
        public SecurityFilterChain onlineChain(HttpSecurity http, TokenService tokenService,
                                               SecurityProperties properties) throws Exception {
            http.antMatcher("/**")
                    .csrf().disable()
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .authorizeRequests().anyRequest().permitAll()
                    .and()
                    .addFilterBefore(new OnlineTokenFilter(tokenService, properties),
                            UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }

        /** 401/403 统一返回 JSON（不带登录页重定向） */
        private void restExceptionHandling(HttpSecurity http) throws Exception {
            http.exceptionHandling()
                    .authenticationEntryPoint((request, response, e) -> write(response, GlobalErrorCode.UNAUTHORIZED))
                    .accessDeniedHandler((request, response, e) -> write(response, GlobalErrorCode.FORBIDDEN));
        }
    }

    /**
     * 无 Redis 退化装配：token 体系不可用，注册全放行链兜底，
     * 避免 Spring Boot 默认 basic 认证接管全部接口导致业务接口 401。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("org.springframework.data.redis.core.StringRedisTemplate")
    static class NoRedisDegradedConfiguration {

        @Bean
        public SecurityFilterChain degradedPermitAllChain(HttpSecurity http) throws Exception {
            http.csrf().disable()
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .authorizeRequests().anyRequest().permitAll();
            return http.build();
        }
    }

    /** 401/403 统一返回 JSON（不带登录页重定向），链装配与退化链共用 */
    private static void write(HttpServletResponse response, GlobalErrorCode errorCode) throws java.io.IOException {
        response.setStatus(errorCode.getHttpStatus() == -1 ? 200 : errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":" + errorCode.getCode()
                + ",\"message\":\"" + errorCode.getMessage()
                + "\",\"traceId\":\"" + org.slf4j.MDC.get("traceId") + "\"}");
        response.getWriter().flush();
    }

    /** micrometer 在场时绑定真实打点；嵌套类经 ASM 元数据判定条件，类加载安全 */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MicrometerCounterMetricsConfiguration {

        @org.springframework.context.annotation.Bean
        com.sfi.scaffold.common.metrics.CounterMetrics micrometerCounterMetrics(
                org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> registry) {
            return new com.sfi.scaffold.common.metrics.MicrometerCounterMetrics(registry.getIfAvailable());
        }
    }

    /** 兜底空实现：micrometer 不在场时静默 */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(com.sfi.scaffold.common.metrics.CounterMetrics.class)
    static class DefaultCounterMetricsConfiguration {

        @org.springframework.context.annotation.Bean
        com.sfi.scaffold.common.metrics.CounterMetrics noopCounterMetrics() {
            return com.sfi.scaffold.common.metrics.CounterMetrics.NOOP;
        }
    }
}
