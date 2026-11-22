package com.sfi.scaffold.starter.security.config;

import com.sfi.scaffold.common.exception.GlobalErrorCode;
import com.sfi.scaffold.common.result.R;
import com.sfi.scaffold.common.util.ServletUtils;
import com.sfi.scaffold.starter.security.anonymous.AnonymousScanner;
import com.sfi.scaffold.starter.security.permission.PermissionChecker;
import com.sfi.scaffold.starter.security.permission.UrlPermissionInterceptor;
import com.sfi.scaffold.starter.security.permission.UrlPermissionProvider;
import com.sfi.scaffold.starter.security.security.ScaffoldSecurityCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 安全边界自动装配：单条 SecurityFilterChain。
 *
 * <p>放行清单只认两类来源（意图跟代码走）：① {@code @Anonymous} 注解接口，
 * ② yml {@code scaffold.security.permit-urls} 兜底代码外路径；随后依次应用
 * {@link ScaffoldSecurityCustomizer} 扩展点（认证组件与项目规则在此挂载），
 * 最后 {@code anyRequest().authenticated()} 兜底——框架只管边界，默认全关。</p>
 *
 * <p>401/403 统一返回 JSON（带 traceId），无登录页重定向。</p>
 *
 * @author lianghang
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ScaffoldSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain scaffoldSecurityChain(HttpSecurity http,
                                                     SecurityProperties properties,
                                                     ObjectProvider<RequestMappingHandlerMapping> handlerMapping,
                                                     ObjectProvider<ScaffoldSecurityCustomizer> customizers) throws Exception {
        http.csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests();
        if (handlerMapping.getIfAvailable() != null) {
            new AnonymousScanner(handlerMapping.getIfAvailable()).registerPermitAll(http);
        }
        if (!properties.getPermitUrls().isEmpty()) {
            http.authorizeRequests()
                    .antMatchers(properties.getPermitUrls().toArray(new String[0])).permitAll();
        }
        for (ScaffoldSecurityCustomizer customizer : customizers) {
            customizer.customize(http);
        }
        http.authorizeRequests().anyRequest().authenticated()
                .and()
                .exceptionHandling()
                .authenticationEntryPoint((request, response, e) ->
                        writeError(response, GlobalErrorCode.UNAUTHORIZED))
                .accessDeniedHandler((request, response, e) ->
                        writeError(response, GlobalErrorCode.FORBIDDEN));
        return http.build();
    }

    private static void writeError(javax.servlet.http.HttpServletResponse response,
                                   GlobalErrorCode errorCode) {
        response.setStatus(errorCode.getHttpStatus());
        ServletUtils.writeJSON(response, R.fail(errorCode));
    }

    @Bean
    public com.sfi.scaffold.starter.security.apiencrypt.ApiEncryptRequestBodyAdvice apiEncryptRequestBodyAdvice() {
        return new com.sfi.scaffold.starter.security.apiencrypt.ApiEncryptRequestBodyAdvice();
    }

    @Bean
    public com.sfi.scaffold.starter.security.apiencrypt.ApiEncryptResponseBodyAdvice apiEncryptResponseBodyAdvice(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new com.sfi.scaffold.starter.security.apiencrypt.ApiEncryptResponseBodyAdvice(objectMapper);
    }

    /** 权限裁决器（编程式 has/require 与 URL 拦截器共用）。 */
    @Bean
    public PermissionChecker permissionChecker() {
        return new PermissionChecker();
    }

    /**
     * URL 权限拦截器：认证之后按 URL + 方法匹配业务提供的规则做 any-of 校验。
     *
     * <p>{@link UrlPermissionProvider} 缺席时传 null，非严格模式下退化为「只认证不授权」，
     * 保证无菜单体系的项目仍可用。业务实现该 Bean 即接管 URL→权限映射。</p>
     *
     * <p>{@code scaffold.security.url-permission-strict=true} 时，未命中任何规则的 URL
     * 直接 403；{@code permit-urls} 与 {@code @Anonymous} 始终短路放行。</p>
     */
    @Bean
    @ConditionalOnMissingBean(UrlPermissionInterceptor.class)
    public UrlPermissionInterceptor urlPermissionInterceptor(
            PermissionChecker permissionChecker,
            SecurityProperties properties,
            ObjectProvider<UrlPermissionProvider> urlPermissionProvider) {
        return new UrlPermissionInterceptor(urlPermissionProvider.getIfAvailable(), permissionChecker,
                properties.isUrlPermissionStrict(), properties.getPermitUrls());
    }

    @Bean
    public WebMvcConfigurer urlPermissionInterceptorConfigurer(UrlPermissionInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/**");
            }
        };
    }
}
