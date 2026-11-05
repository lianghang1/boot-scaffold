package com.sfi.scaffold.starter.protection;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.starter.protection.idempotent.Idempotent;
import com.sfi.scaffold.starter.protection.idempotent.IdempotentAspect;
import com.sfi.scaffold.starter.protection.identity.RequestIdentity;
import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 幂等切面测试：窗口拦截、默认键/SpEL 业务键、异常删键、登录身份优先、无请求上下文放行。
 *
 * @author lianghang
 */
class IdempotentAspectTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private IdempotentAspect aspect;
    private ProceedingJoinPoint pjp;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        aspect = new IdempotentAspect(redisTemplate, new RequestIdentity(null));
        pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{"a1", null});
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void signatureWithRealMethod() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("annotated", Long.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getSignature()).thenReturn(signature);
    }

    /** 供 SpEL 参数名发现的真实方法 */
    void annotated(Long bizNo) {
    }

    private Idempotent idempotent(int windowSeconds) {
        return idempotent(windowSeconds, "", true);
    }

    private Idempotent idempotent(int windowSeconds, final String key, final boolean deleteOnException) {
        return new Idempotent() {
            @Override
            public int windowSeconds() {
                return windowSeconds;
            }

            @Override
            public String key() {
                return key;
            }

            @Override
            public boolean deleteOnException() {
                return deleteOnException;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Idempotent.class;
            }
        };
    }

    @Test
    void firstCallShouldWin() throws Throwable {
        bindRequest(new MockHttpServletRequest("POST", "/api/order"));
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(pjp.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.around(pjp, idempotent(10)));
    }

    @Test
    void duplicateWithinWindowShouldBeRejected() {
        bindRequest(new MockHttpServletRequest("POST", "/api/order"));
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        BizException e = assertThrows(BizException.class,
                () -> aspect.around(pjp, idempotent(10)));
        assertEquals(1409, e.getErrorCode().getCode());
    }

    @Test
    void defaultKeyShouldCoverMethodUriIdentityAndArgs() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/order");
        request.addHeader("X-Account-Id", "42");
        bindRequest(request);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        assertDoesNotThrow(() -> aspect.around(pjp, idempotent(15)));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).setIfAbsent(key.capture(), anyString(), eq(Duration.ofSeconds(15)));
        assertTrue(key.getValue().contains("POST:/api/order:hdr:42:"),
                "默认幂等键应包含方法+URI+身份维度，实际=" + key.getValue());
    }

    @Test
    void spelKeyShouldOverrideDefaultKey() throws Throwable {
        signatureWithRealMethod();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/order");
        request.addHeader("X-Account-Id", "42");
        bindRequest(request);
        when(pjp.getArgs()).thenReturn(new Object[]{777L});
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        assertDoesNotThrow(() -> aspect.around(pjp, idempotent(10, "'pay:' + #bizNo", true)));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).setIfAbsent(key.capture(), anyString(), eq(Duration.ofSeconds(10)));
        assertEquals("scaffold:idempotent:pay:777", key.getValue());
    }

    @Test
    void businessExceptionShouldDeleteKeyForRetry() throws Throwable {
        bindRequest(new MockHttpServletRequest("POST", "/api/order"));
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(pjp.proceed()).thenThrow(new IllegalStateException("业务失败"));

        assertThrows(IllegalStateException.class, () -> aspect.around(pjp, idempotent(10)));

        verify(redisTemplate).delete(anyString());
    }

    @Test
    void deleteDisabledShouldKeepKeyOnException() throws Throwable {
        bindRequest(new MockHttpServletRequest("POST", "/api/order"));
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(pjp.proceed()).thenThrow(new IllegalStateException("业务失败"));

        assertThrows(IllegalStateException.class,
                () -> aspect.around(pjp, idempotent(10, "", false)));

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void loginIdentityShouldBeatGatewayHeader() {
        LoginUser user = new LoginUser();
        user.setAccountId(7L);
        LoginContext.set(user);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/order");
        request.addHeader("X-Account-Id", "42");
        bindRequest(request);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        IdempotentAspect securityAware = new IdempotentAspect(redisTemplate,
                new RequestIdentity(new com.sfi.scaffold.starter.protection.identity.LoginIdentityLookup()));
        assertDoesNotThrow(() -> securityAware.around(pjp, idempotent(10)));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).setIfAbsent(key.capture(), anyString(), any());
        assertTrue(key.getValue().contains(":acct:7:"),
                "登录身份应优先于网关 header，实际=" + key.getValue());
    }

    @Test
    void noRequestContextShouldBypassWithoutRedisCall() throws Throwable {
        when(pjp.proceed()).thenReturn("ok");
        assertEquals("ok", aspect.around(pjp, idempotent(10)));
        verifyNoInteractions(redisTemplate);
    }
}
