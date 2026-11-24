package com.sfi.scaffold.starter.data.jpa.logicdelete;

import com.sfi.scaffold.query.core.IncludeDeletedHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * JPA 通路逻辑删除切面：每次仓储调用前对当前 Session 启用 {@code scaffold_deleted}
 * Hibernate 过滤器，标注 {@code @Filter(name=..., condition="deleted = false")} 的实体
 * 查询自动追加逻辑删除条件；方法退出关闭。
 *
 * <p>与租户切面同构（Filter 挂 Session、无事务自动开 REQUIRED、FilterDef 未注册时
 * 静默放行、对 em.find() 与原生 SQL 不生效由 findById 切面兜底）。差异：逻辑删除
 * 不依赖登录态，全局生效；豁免标记复用 query 通路的 {@link IncludeDeletedHolder}，
 * 使 {@code @IncludeDeleted} 在 jpa/query 两条通路同时生效。</p>
 *
 * @author lianghang
 */
@Aspect
public class JpaDeletedFilterAspect {

    /** 与 BaseEntity 上 @FilterDef 同名；实体以 @Filter 白名单式加入。 */
    public static final String FILTER_NAME = "scaffold_deleted";

    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionTemplate transactionTemplate;

    public JpaDeletedFilterAspect(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Pointcut("execution(public * org.springframework.data.repository.Repository+.*(..))")
    public void repositoryCall() {
    }

    @Around("repositoryCall()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        if (IncludeDeletedHolder.isMarked()) {
            return point.proceed();
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return enableFilterAndProceed(point);
        }
        return transactionTemplate.execute(status -> {
            try {
                return enableFilterAndProceed(point);
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        });
    }

    private Object enableFilterAndProceed(ProceedingJoinPoint point) throws Throwable {
        Session session = entityManager.unwrap(Session.class);
        // FilterDef 未注册（PU 无逻辑删除实体）时静默放行，避免硬失败
        if (!session.getSessionFactory().getDefinedFilterNames().contains(FILTER_NAME)) {
            return point.proceed();
        }
        Filter filter = session.enableFilter(FILTER_NAME);
        try {
            return point.proceed();
        } finally {
            session.disableFilter(FILTER_NAME);
        }
    }
}
