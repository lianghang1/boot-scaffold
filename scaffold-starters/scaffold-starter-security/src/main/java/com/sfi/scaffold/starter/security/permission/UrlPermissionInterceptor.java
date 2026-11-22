package com.sfi.scaffold.starter.security.permission;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.common.exception.GlobalErrorCode;
import com.sfi.scaffold.starter.security.anonymous.Anonymous;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * URL 权限拦截器：按请求的 URL + HTTP 方法，用 Spring {@link AntPathMatcher} 匹配
 * {@link UrlPermissionProvider} 提供的规则，命中则校验权限点（any-of）。
 *
 * <p><b>裁决规则</b>：</p>
 * <ol>
 *   <li>{@code @Anonymous} 标记的方法/类短路放行——认证边界已在 SecurityFilterChain
 *       的 permitAll 放开，这里不再授权收敛；</li>
 *   <li>{@code permit-urls} 白名单命中短路放行——框架级公开地址（静态资源、三方 jar
 *       接口），不受 URL 权限与严格模式影响；</li>
 *   <li>非 HandlerMethod（静态资源等）放行；</li>
 *   <li>多条规则同时命中时取最具体的一条（Spring 的 pattern 比较器排序）；</li>
 *   <li>命中规则的权限点为空表示「登录即可」，放行；否则 any-of 校验，无权抛 403；</li>
 *   <li><b>没有任何规则命中</b>：严格模式抛 403（强制补齐 pattern），否则放行——
 *       认证仍由 {@code anyRequest().authenticated()} 兜底。</li>
 * </ol>
 *
 * <p>路径匹配用 Spring 自带 {@code AntPathMatcher}，支持问号、单星、双星三种通配符，
 * 不另造通配语法。</p>
 *
 * @author lianghang
 */
public class UrlPermissionInterceptor implements HandlerInterceptor {

    private final UrlPermissionProvider provider;
    private final PermissionChecker permissionChecker;
    private final boolean strict;
    private final List<String> permitUrls;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public UrlPermissionInterceptor(UrlPermissionProvider provider, PermissionChecker permissionChecker) {
        this(provider, permissionChecker, false, null);
    }

    public UrlPermissionInterceptor(UrlPermissionProvider provider, PermissionChecker permissionChecker,
                                    boolean strict, List<String> permitUrls) {
        this.provider = provider;
        this.permissionChecker = permissionChecker;
        this.strict = strict;
        this.permitUrls = permitUrls;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod hm = (HandlerMethod) handler;
        if (isAnonymous(hm)) {
            return true;
        }

        String path = request.getRequestURI();
        if (isPermitted(path)) {
            return true;
        }

        String method = request.getMethod();
        List<UrlPermissionRule> rules = provider == null ? null : provider.loadRules();
        if (rules == null || rules.isEmpty()) {
            return allowWhenNoRule(path);
        }

        List<UrlPermissionRule> matched = new ArrayList<>();
        for (UrlPermissionRule rule : rules) {
            if (rule != null && rule.matchesMethod(method)
                    && pathMatcher.match(rule.getPattern(), path)) {
                matched.add(rule);
            }
        }
        if (matched.isEmpty()) {
            return allowWhenNoRule(path);
        }

        // 多条命中取最具体的 pattern（Spring 比较器：常量 > 单星 > 双星）
        matched.sort((a, b) -> pathMatcher.getPatternComparator(path)
                .compare(a.getPattern(), b.getPattern()));
        List<String> permissions = matched.get(0).getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            return true;
        }
        permissionChecker.require(permissions.toArray(new String[0]));
        return true;
    }

    /** 无规则命中：严格模式拒绝（强制补 pattern），否则放行。 */
    private boolean allowWhenNoRule(String path) {
        if (strict) {
            throw BizException.of(GlobalErrorCode.FORBIDDEN,
                    "URL 未配置权限规则，拒绝访问：" + path);
        }
        return true;
    }

    /** permit-urls 白名单：ant 风格，命中即框架级放行。 */
    private boolean isPermitted(String path) {
        if (permitUrls == null || permitUrls.isEmpty()) {
            return false;
        }
        for (String pattern : permitUrls) {
            if (pattern != null && pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAnonymous(HandlerMethod hm) {
        return hm.getMethodAnnotation(Anonymous.class) != null
                || hm.getBeanType().getAnnotation(Anonymous.class) != null;
    }
}
