package com.sfi.scaffold.starter.tenant.aspect;

import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;
import com.sfi.scaffold.starter.tenant.core.TenantIgnoreHolder;
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
 * JPA 通路租户过滤切面：每次仓储调用前对当前 Session 启用 {@code scaffold_tenant}
 * Hibernate 过滤器（参数取登录身份的 tenantId），标注 {@code @Filter} 的实体
 * 查询自动追加租户条件；方法退出关闭。
 *
 * <p><b>事务时序</b>：Filter 挂在 Session 上，无事务时每次查询各取独立 Session、
 * Filter 静默失效。与 {@code DataScopeAspect} 同构的处理：已在事务内则复用当前
 * Session，否则用 {@link TransactionTemplate}（REQUIRED）自动开启——两种拦截顺序
 * 下语义一致。</p>
 *
 * <p><b>放行条件</b>：平台身份（登录身份无 tenantId）、{@code @TenantIgnore} 豁免
 * 标记期间——全放行不启用过滤器。未标注 {@code @Filter} 的实体不受影响（白名单式）。</p>
 *
 * <p><b>已知边界</b>（Hibernate 机制决定）：对 {@code em.find()}/findById 不生效
 * （由 {@link FindByIdTenantAspect} 兜底）、对原生 SQL 不生效；仓储方法内再调仓储
 * 时内层退出会提前关闭过滤器（仓储应为叶子 Bean，不做仓储套仓储）。</p>
 *
 * @author lianghang
 */
@Aspect
public class JpaTenantFilterAspect {

    /**
     * 与 BaseEntity 上 FilterDef 同名；实体以
     * {@code @Filter(name = JpaTenantFilterAspect.FILTER_NAME, condition = "tenant_id = :tenantId")}
     * 白名单式加入（Hibernate 5.6 不支持 @Filter 组合注解，须直写）。
     */
    public static final String FILTER_NAME = "scaffold_tenant";

    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionTemplate transactionTemplate;

    public JpaTenantFilterAspect(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Pointcut("execution(public * org.springframework.data.repository.Repository+.*(..))")
    public void repositoryCall() {
    }

    @Around("repositoryCall()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        LoginUser user = LoginContext.currentOrNull();
        String tenantId = user == null ? null : user.getTenantId();
        if (tenantId == null || tenantId.isEmpty() || TenantIgnoreHolder.isMarked()) {
            return point.proceed();
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return enableFilterAndProceed(point, tenantId);
        }
        // 无事务：自动开一个（REQUIRED），保证 Filter 挂在贯穿全部查询的 Session 上
        return transactionTemplate.execute(status -> {
            try {
                return enableFilterAndProceed(point, tenantId);
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        });
    }

    private Object enableFilterAndProceed(ProceedingJoinPoint point, String tenantId) throws Throwable {
        Session session = entityManager.unwrap(Session.class);
        // FilterDef 未注册（PU 无 BaseEntity 系实体）时静默放行，避免硬失败
        if (!session.getSessionFactory().getDefinedFilterNames().contains(FILTER_NAME)) {
            return point.proceed();
        }
        Filter filter = session.enableFilter(FILTER_NAME);
        filter.setParameter("tenantId", tenantId);
        try {
            return point.proceed();
        } finally {
            session.disableFilter(FILTER_NAME);
        }
    }
}
