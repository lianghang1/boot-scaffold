package com.sfi.scaffold.starter.security;

import com.sfi.scaffold.starter.security.identity.AccountType;
import com.sfi.scaffold.starter.security.identity.IdentityContextSupport;
import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;
import com.sfi.scaffold.starter.security.token.SessionInfo;
import com.sfi.scaffold.starter.security.token.TokenService;
import com.sfi.scaffold.starter.security.token.filter.TokenAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * token 认证过滤器语义：有效 token 装入上下文；无效/缺失静默继续链，
 * 不写 401（授权段裁决）。
 *
 * @author lianghang
 */
class TokenAuthenticationFilterTest {

    private final TokenService tokenService = mock(TokenService.class);
    private final TokenAuthenticationFilter filter = new TokenAuthenticationFilter(tokenService);

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final boolean[] chainInvoked = {false};
    private final FilterChain chain = (req, res) -> chainInvoked[0] = true;

    @AfterEach
    void cleanUp() {
        IdentityContextSupport.clear();
    }

    @Test
    void validTokenPopulatesLoginContext() throws Exception {
        SessionInfo session = new SessionInfo();
        session.setAccountId(1L);
        session.setAccountNo("u1");
        session.setAccountType(AccountType.OPERATOR.name());
        when(tokenService.resolveWithRenew("tok-1")).thenReturn(session);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Token", "tok-1");
        filter.doFilter(request, response, chain);

        assertTrue(chainInvoked[0]);
    }

    @Test
    void invalidTokenContinuesAnonymously() throws Exception {
        when(tokenService.resolveWithRenew(anyString())).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Token", "bad-token");
        filter.doFilter(request, response, chain);

        assertTrue(chainInvoked[0]);
        assertEquals(200, response.getStatus());
        assertNull(LoginContext.currentOrNull());
    }

    @Test
    void missingTokenContinuesAnonymously() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertTrue(chainInvoked[0]);
        assertNull(LoginContext.currentOrNull());
    }

    @Test
    void contextClearedAfterRequest() throws Exception {
        SessionInfo session = new SessionInfo();
        session.setAccountId(1L);
        session.setAccountNo("u1");
        session.setAccountType(AccountType.OPERATOR.name());
        when(tokenService.resolveWithRenew("tok-1")).thenReturn(session);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Token", "tok-1");
        final LoginUser[] seen = new LoginUser[1];
        FilterChain inner = (req, res) -> seen[0] = LoginContext.currentOrNull();
        filter.doFilter(request, response, inner);

        assertEquals("u1", seen[0].getAccountNo());
        assertNull(LoginContext.currentOrNull());
    }
}
