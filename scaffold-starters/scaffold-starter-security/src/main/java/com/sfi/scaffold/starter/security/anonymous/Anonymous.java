package com.sfi.scaffold.starter.security.anonymous;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 匿名放行声明：标注在 Controller 方法或类上，接口无需认证即可访问。
 *
 * <p>启动时由 {@link AnonymousScanner} 扫描 RequestMappingHandlerMapping 构建
 * 放行清单，注册进安全链——意图跟着代码走，不再依赖 yml 白名单拼写路径。
 * 仅用于确实公开的接口（健康检查、开放回调、静态数据）；业务接口一律默认认证。</p>
 *
 * @author lianghang
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Anonymous {
}
