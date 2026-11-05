package com.sfi.scaffold.starter.protection.lock;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.common.exception.GlobalErrorCode;
import com.sfi.scaffold.starter.protection.support.SpelKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * 分布式锁切面，配合 {@link DistributedLock} 注解工作。
 *
 * <p>锁 key 支持 SpEL 表达式（如 "'order:' + #p0"），取锁失败抛 1409 业务异常；
 * finally 确保解锁——注解即锁，业务代码零侵入。</p>
 *
 * @author lianghang
 */
@Aspect
public class DistributedLockAspect {

    private final RedisLockUtil lockUtil;

    public DistributedLockAspect(RedisLockUtil lockUtil) {
        this.lockUtil = lockUtil;
    }

    @Around("@annotation(lock)")
    public Object around(ProceedingJoinPoint pjp, DistributedLock lock) throws Throwable {
        String fullKey = lock.key() + ":" + SpelKeyResolver.resolve(pjp, lock.key());
        String holder = lockUtil.tryLock(fullKey, lock.waitMillis(), lock.leaseSeconds());
        if (holder == null) {
            throw BizException.of(GlobalErrorCode.CONFLICT, "操作冲突，请稍后重试（获取锁失败）");
        }
        try {
            return pjp.proceed();
        } finally {
            lockUtil.unlock(fullKey, holder);
        }
    }
}
