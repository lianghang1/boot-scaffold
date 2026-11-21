package com.sfi.scaffold.starter.data.jpa.datascope;

import com.sfi.scaffold.common.datascope.DataScopeDecision;
import com.sfi.scaffold.common.datascope.DataScopeHolder;
import com.sfi.scaffold.common.datascope.DataScopeType;
import com.sfi.scaffold.starter.data.jpa.audit.ScaffoldAuditorHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * 数据权限 JPA 通路切面：两种触发模式。
 *
 * <p><b>① 静态注解模式</b>（{@link DataScope}）：服务方法/类标注声明档位，
 * 按 value 启用 self filter。无登录态 fail-closed、事务自动兜底——保持框架既有语义，
 * 供系统任务/单测等无登录身份场景使用。</p>
 *
 * <p><b>② 动态决策模式</b>（新增）：拦截所有 Repository 调用，从
 * {@link DataScopeHolder} 读登录时归约的 {@link DataScopeDecision}，按决策启用
 * self / dept filter（deptIds 与 self 可并存，各自命中即可见）。业务表只需在类上标
 * {@code @Filter(name=...)} 白名单即可自动生效，无需逐方法标注。</p>
 *
 * <p>触发优先级：方法带 {@link DataScope} 注解时走静态模式（显式声明优先），否则走动态模式。
 * 两种模式的 Filter 都挂在 Session 上，方法退出按启用情况逐一关闭。</p>
 *
 * <p>事务时序：已有事务则复用当前 Session，否则用 {@link TransactionTemplate}（REQUIRED）
 * 自动开启——Filter 必须挂在贯穿全部查询的 Session 上才有效，与注解顺序无关。</p>
 *
 * <p>已知边界（Hibernate Filter 机制决定）：对 HQL / Criteria 查询生效，
 * 对 {@code em.find()} 按主键直达与原生 SQL 不生效；仓储应作叶子 Bean，避免仓储套仓储。</p>
 *
 * @author lianghang
 */
@Aspect
public class DataScopeAspect {

    /** 与 BaseEntity 上 @FilterDef 同名；实体以 @Filter(name=...) 白名单式加入。 */
    public static final String FILTER_SELF = "scaffold_data_scope_self";
    public static final String FILTER_DEPT = "scaffold_data_scope_dept";

    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionTemplate transactionTemplate;

    public DataScopeAspect(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 静态注解：显式声明档位的方法/类 */
    @Pointcut("@within(com.sfi.scaffold.starter.data.jpa.datascope.DataScope)"
            + " || @annotation(com.sfi.scaffold.starter.data.jpa.datascope.DataScope)")
    public void dataScopePointcut() {
    }

    /** 动态决策：所有仓储调用（无注解时从 Holder 读决策） */
    @Pointcut("execution(public * org.springframework.data.repository.Repository+.*(..))")
    public void repositoryCall() {
    }

    @Around("dataScopePointcut() || repositoryCall()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        DataScope scope = resolve(point);
        if (scope != null) {
            return applyStatic(point, scope.value());
        }
        DataScopeDecision decision = DataScopeHolder.get();
        if (decision == null || decision.isUnrestricted()) {
            return point.proceed();
        }
        return inTransaction(() -> applyDynamic(point, decision));
    }

    /** 静态注解模式：按声明档位启用 self filter。 */
    private Object applyStatic(ProceedingJoinPoint point, DataScopeType type) throws Throwable {
        if (type == null || type == DataScopeType.ALL) {
            return point.proceed();
        }
        // 静态模式当前仅 SELF 档由 Hibernate Filter 支撑；部门档走动态决策
        String owner = ScaffoldAuditorHolder.get();
        if (owner == null || owner.isEmpty()) {
            throw new IllegalStateException(
                    "@DataScope(SELF) 需要登录态（ScaffoldAuditorHolder 未写入），拒绝执行以防越权");
        }
        return inTransaction(() -> withSelfFilter(point, owner));
    }

    /** 动态决策模式：按归约结果启用 self / dept filter。 */
    private Object applyDynamic(ProceedingJoinPoint point, DataScopeDecision decision) throws Throwable {
        String owner = decision.isSelf() ? ScaffoldAuditorHolder.get() : null;
        boolean hasSelf = decision.isSelf() && owner != null && !owner.isEmpty();
        boolean hasDept = !decision.getDeptIds().isEmpty();
        if (!hasSelf && !hasDept) {
            // 决策既无可用本人也无部门：fail-closed 不放全量
            throw new IllegalStateException("数据权限决策为空（无本人标识且无部门集合），拒绝执行以防越权");
        }
        Session session = entityManager.unwrap(Session.class);
        boolean selfOn = hasSelf && filterDefined(session, FILTER_SELF);
        boolean deptOn = hasDept && filterDefined(session, FILTER_DEPT);
        try {
            if (selfOn) {
                session.enableFilter(FILTER_SELF).setParameter("owner", owner);
            }
            if (deptOn) {
                session.enableFilter(FILTER_DEPT).setParameterList("deptIds", decision.getDeptIds());
            }
            return point.proceed();
        } finally {
            if (selfOn) {
                session.disableFilter(FILTER_SELF);
            }
            if (deptOn) {
                session.disableFilter(FILTER_DEPT);
            }
        }
    }

    private Object withSelfFilter(ProceedingJoinPoint point, String owner) throws Throwable {
        Session session = entityManager.unwrap(Session.class);
        if (!filterDefined(session, FILTER_SELF)) {
            return point.proceed();
        }
        session.enableFilter(FILTER_SELF).setParameter("owner", owner);
        try {
            return point.proceed();
        } finally {
            session.disableFilter(FILTER_SELF);
        }
    }

    private boolean filterDefined(Session session, String name) {
        return session.getSessionFactory().getDefinedFilterNames().contains(name);
    }

    /** 已在事务内直接执行，否则自动开 REQUIRED 事务保证 Filter 贯穿全部查询。 */
    private Object inTransaction(ThrowingSupplier supplier) throws Throwable {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return supplier.get();
        }
        return transactionTemplate.execute(status -> {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        });
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws Throwable;
    }

    /** 方法标注优先，其次目标类标注。 */
    private DataScope resolve(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        DataScope scope = signature.getMethod().getAnnotation(DataScope.class);
        if (scope == null) {
            scope = point.getTarget().getClass().getAnnotation(DataScope.class);
        }
        return scope;
    }
}
