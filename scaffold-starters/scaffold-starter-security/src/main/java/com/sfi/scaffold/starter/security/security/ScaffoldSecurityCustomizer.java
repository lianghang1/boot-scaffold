package com.sfi.scaffold.starter.security.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * 安全链扩展点：项目与认证组件各自注册 Bean，链构建时逐个应用。
 *
 * <p>执行时机在 @Anonymous 与 yml 白名单注册之后、{@code anyRequest().authenticated()}
 * 兜底之前——实现里追加的 authorizeRequests 规则与 addFilterBefore 过滤器
 * 均落在兜底之前生效。认证组件（token/openapi 等）经本接口把自己的
 * 认证过滤器挂进链中，框架对认证方式零预设。</p>
 *
 * @author lianghang
 */
@FunctionalInterface
public interface ScaffoldSecurityCustomizer {

    void customize(HttpSecurity http) throws Exception;
}
