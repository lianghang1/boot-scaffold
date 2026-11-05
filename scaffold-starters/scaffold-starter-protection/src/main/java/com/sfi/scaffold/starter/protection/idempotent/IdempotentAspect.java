package com.sfi.scaffold.starter.protection.idempotent;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.common.exception.GlobalErrorCode;
import com.sfi.scaffold.common.constant.RedisKeys;
import com.sfi.scaffold.starter.protection.identity.RequestIdentity;
import com.sfi.scaffold.starter.protection.support.SpelKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 接口幂等切面，配合 {@link Idempotent} 注解工作。
 *
 * <p>幂等键经 setIfAbsent 占位，窗口内重复请求直接拒绝——占位即提交登记，
 * 首个到达者胜出，后续者一律 1409。业务异常时按 {@code deleteOnException}
 * 删除占位键（异常说明业务未发生，窗口内应允许重试）；拒绝路径不删键。</p>
 *
 * @author lianghang
 */
@Aspect
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);

    private final StringRedisTemplate redisTemplate;
    private final RequestIdentity requestIdentity;

    public IdempotentAspect(StringRedisTemplate redisTemplate, RequestIdentity requestIdentity) {
        this.redisTemplate = redisTemplate;
        this.requestIdentity = requestIdentity;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        org.springframework.web.context.request.ServletRequestAttributes attrs =
                (org.springframework.web.context.request.ServletRequestAttributes)
                        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return pjp.proceed();
        }
        HttpServletRequest request = attrs.getRequest();
        String key = buildKey(pjp, idempotent, request);
        Boolean first = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(idempotent.windowSeconds()));
        if (first == null || !first) {
            log.warn("[idempotent] 重复提交拦截: key={}", key);
            throw BizException.of(GlobalErrorCode.CONFLICT, "请勿重复提交");
        }
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            if (idempotent.deleteOnException()) {
                redisTemplate.delete(key);
            }
            throw t;
        }
    }

    /** 幂等键：声明了 key（SpEL）用业务键；否则 身份 + URI + 参数摘要。 */
    private String buildKey(ProceedingJoinPoint pjp, Idempotent idempotent, HttpServletRequest request) {
        String businessKey = SpelKeyResolver.resolve(pjp, idempotent.key());
        if (!businessKey.isEmpty()) {
            return RedisKeys.IDEMPOTENT + businessKey;
        }
        String identity = requestIdentity.resolve(request);
        String argsDigest = md5(Arrays.stream(pjp.getArgs())
                .map(a -> a == null ? "null" : String.valueOf(a.hashCode()))
                .collect(Collectors.joining("|")));
        return RedisKeys.IDEMPOTENT + request.getMethod() + ":"
                + request.getRequestURI() + ":" + identity + ":" + argsDigest;
    }

    private String md5(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }
}
