package com.sfi.scaffold.starter.security.permission;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 一条 URL 权限规则：HTTP 方法 + Ant 路径模式 + 所需权限点（any-of）。
 *
 * <p>路径匹配交给 Spring 的 {@link org.springframework.util.AntPathMatcher}，支持
 * {@code ?}、{@code *}、{@code **}；方法为 {@code *} 或空表示不限方法。</p>
 *
 * @author lianghang
 */
public final class UrlPermissionRule {

    private final String method;
    private final String pattern;
    private final List<String> permissions;

    public UrlPermissionRule(String method, String pattern, String... permissions) {
        this.method = method == null || method.trim().isEmpty() ? "*" : method.trim().toUpperCase();
        this.pattern = pattern;
        this.permissions = permissions == null || permissions.length == 0
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(Arrays.asList(permissions));
    }

    public String getMethod() {
        return method;
    }

    public String getPattern() {
        return pattern;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    /** 方法是否命中：{@code *} 匹配任意方法。 */
    public boolean matchesMethod(String requestMethod) {
        if ("*".equals(method) || requestMethod == null) {
            return true;
        }
        return method.equalsIgnoreCase(requestMethod);
    }
}
