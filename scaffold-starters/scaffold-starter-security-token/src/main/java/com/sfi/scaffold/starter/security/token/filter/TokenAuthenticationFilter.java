package com.sfi.scaffold.starter.security.token.filter;

import com.sfi.scaffold.starter.security.identity.IdentityContextSupport;
import com.sfi.scaffold.starter.security.token.SessionInfo;
import com.sfi.scaffold.starter.security.token.TokenService;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * token 会话认证过滤器：请求携带有效 token 则装入登录上下文，
 * 无 token / token 无效一律静默继续链（授权段裁决，匿名路径与 token 校验解耦）。
 *
 * <p>经 {@code ScaffoldSecurityCustomizer} 挂进边界链；finally 强制清理上下文，
 * 防线程复用串号——认证态的生命周期严格限定在单次请求内。</p>
 *
 * @author lianghang
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    public TokenAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String token = IdentityContextSupport.extractToken(request);
            if (token != null && !token.isEmpty()) {
                SessionInfo session = tokenService.resolveWithRenew(token);
                if (session != null) {
                    IdentityContextSupport.applyUser(session.toLoginUser());
                }
            }
            chain.doFilter(request, response);
        } finally {
            IdentityContextSupport.clear();
        }
    }
}
