package com.sfi.scaffold.starter.security.identity;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 认证组件共用的身份装配工具：登录用户写入 LoginContext 与 SecurityContext
 * 双轨、请求结束时强制清理、token 提取。各认证组件
 * 认证成功后统一经本类归一化，授权层（PermissionInterceptor）只认 LoginContext。
 *
 * @author lianghang
 */
public final class IdentityContextSupport {

    private IdentityContextSupport() {
    }

    /** 登录用户装入上下文：LoginContext + SecurityContext（角色自动补 ROLE_ 前缀）。 */
    public static void applyUser(LoginUser user) {
        LoginContext.set(user);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        Set<String> roles = user.getRoles();
        if (roles != null) {
            for (String role : roles) {
                authorities.add(new SimpleGrantedAuthority(
                        role.startsWith("ROLE_") ? role : "ROLE_" + role));
            }
        }
        Set<String> permissions = user.getPermissions();
        if (permissions != null) {
            for (String p : permissions) {
                authorities.add(new SimpleGrantedAuthority(p));
            }
        }
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(user);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** 请求结束强制清理，防线程复用串号。 */
    public static void clear() {
        LoginContext.clear();
        SecurityContextHolder.clearContext();
    }

    /** token 提取：Authorization: Bearer xxx 优先，其次 X-Token。 */
    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return request.getHeader("X-Token");
    }
}
