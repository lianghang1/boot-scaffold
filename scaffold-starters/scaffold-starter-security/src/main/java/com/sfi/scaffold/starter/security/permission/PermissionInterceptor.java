package com.sfi.scaffold.starter.security.permission;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 权限校验拦截器：进入 Controller 前校验 {@link RequirePermission} 声明的权限点。
 *
 * <p>执行位置在认证过滤器之后——{@code LoginContext} 已就绪，裁决委托
 * {@link PermissionChecker}（与编程式用法同一实现，行为天然一致）；
 * 未登录（匿名）抛 401，已登录但无权抛 403，均走全局异常处理器的统一响应结构。
 * 无注解的接口零开销直通——权限校验只对显式声明的管理接口生效。</p>
 *
 * @author lianghang
 */
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionChecker permissionChecker;

    public PermissionInterceptor(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequirePermission required = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (required == null) {
            return true;
        }
        permissionChecker.require(required.value());
        return true;
    }
}
