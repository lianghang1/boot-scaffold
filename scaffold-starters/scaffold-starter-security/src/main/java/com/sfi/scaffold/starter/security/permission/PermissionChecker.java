package com.sfi.scaffold.starter.security.permission;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.common.exception.GlobalErrorCode;
import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 编程式权限裁决：any-of 语义（具备任一权限即通过）。
 *
 * <p>供业务代码使用——按钮显隐、菜单裁剪、service 内条件分支等需要「问一句有没有权」的场景。
 * 与 URL 权限拦截器 {@link UrlPermissionInterceptor} 共用本类做裁决与异常文案，
 * 保证编程式与 URL 拦截两种用法行为一致。</p>
 *
 * @author lianghang
 */
public class PermissionChecker {

    /** 当前登录用户是否拥有任一权限；未登录视为无权（返回 false，不抛异常）。 */
    public boolean has(String... permissions) {
        LoginUser user = LoginContext.currentOrNull();
        if (user == null) {
            return false;
        }
        for (String permission : permissions) {
            if (user.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /** 强制要求权限：未登录抛 401，已登录无权抛 403（与注解路径同一文案）。 */
    public void require(String... permissions) {
        LoginUser user = LoginContext.currentOrNull();
        if (user == null) {
            throw BizException.of(GlobalErrorCode.UNAUTHORIZED);
        }
        for (String permission : permissions) {
            if (user.hasPermission(permission)) {
                return;
            }
        }
        throw BizException.of(GlobalErrorCode.FORBIDDEN, "权限不足，需要："
                + Arrays.stream(permissions).collect(Collectors.joining(" 或 ")));
    }
}
