package com.sfi.scaffold.starter.tenant.aspect;

import com.sfi.scaffold.starter.tenant.annotation.TenantIgnore;
import com.sfi.scaffold.starter.tenant.core.TenantIgnoreHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * {@link TenantIgnore} 方法/类切面：标记期间三条过滤通路（计划类查询、JPA 查询、
 * findById 兜底切面）全部放行，方法返回即按深度递减恢复。
 *
 * @author lianghang
 */
@Aspect
public class TenantIgnoreAspect {

    @Pointcut("@annotation(com.sfi.scaffold.starter.tenant.annotation.TenantIgnore)"
            + " || @within(com.sfi.scaffold.starter.tenant.annotation.TenantIgnore)")
    public void tenantIgnorePointcut() {
    }

    @Around("tenantIgnorePointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        TenantIgnoreHolder.mark();
        try {
            return point.proceed();
        } finally {
            TenantIgnoreHolder.clear();
        }
    }
}
