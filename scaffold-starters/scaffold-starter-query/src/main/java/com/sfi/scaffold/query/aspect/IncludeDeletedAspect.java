package com.sfi.scaffold.query.aspect;

import com.sfi.scaffold.query.annotation.IncludeDeleted;
import com.sfi.scaffold.query.core.IncludeDeletedHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * {@link IncludeDeleted} 方法/类切面：标记期间 query 通路的逻辑删除过滤放行，
 * 方法返回按深度递减恢复。与租户豁免切面各自独立、互不干扰。
 *
 * @author lianghang
 */
@Aspect
public class IncludeDeletedAspect {

    @Pointcut("@annotation(com.sfi.scaffold.query.annotation.IncludeDeleted)"
            + " || @within(com.sfi.scaffold.query.annotation.IncludeDeleted)")
    public void includeDeletedPointcut() {
    }

    @Around("includeDeletedPointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        IncludeDeletedHolder.mark();
        try {
            return point.proceed();
        } finally {
            IncludeDeletedHolder.clear();
        }
    }
}
