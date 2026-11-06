package com.sfi.scaffold.starter.tenant.aspect;

import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;
import com.sfi.scaffold.starter.tenant.annotation.TenantIdField;
import com.sfi.scaffold.starter.tenant.core.TenantIgnoreHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * findById 兜底切面：按主键直取绕过 Hibernate Filter（Hibernate 5.6 硬伤），
 * 由本切面在结果上比对租户——不一致返回空 + WARN 审计（过滤式而非 403：
 * 防 403/空差异造成的主键存在性探测，语义与「查不到数据」统一）。
 *
 * <p>租户字段定位：字段名 {@code tenantId} 自动探测，或字段标注
 * {@link TenantIdField}；两者皆无视为非租户实体，原样放行。平台身份
 * （无 tenantId）与 {@code @TenantIgnore} 豁免期间不比对。</p>
 *
 * @author lianghang
 */
@Aspect
public class FindByIdTenantAspect {

    private static final Logger log = LoggerFactory.getLogger(FindByIdTenantAspect.class);

    private static final Map<Class<?>, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    // 注意：CrudRepository<ID> 泛型无界，findById 擦除后参数为 Object 而非 Serializable，
    // 故用 (*) 匹配任意单参 findById（含业务自定义 findById 重载）
    @Pointcut("execution(public java.util.Optional org.springframework.data.repository.CrudRepository+.findById(*))")
    public void findByIdCall() {
    }

    @Around("findByIdCall()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Object result = point.proceed();
        if (!(result instanceof Optional) || !((Optional<?>) result).isPresent()) {
            return result;
        }
        LoginUser user = LoginContext.currentOrNull();
        String tenantId = user == null ? null : user.getTenantId();
        if (tenantId == null || tenantId.isEmpty() || TenantIgnoreHolder.isMarked()) {
            return result;
        }
        Object entity = ((Optional<?>) result).get();
        Field field = resolveTenantField(ClassUtils.getUserClass(entity.getClass()));
        if (field == null) {
            return result;
        }
        Object value = field.get(entity);
        if (!tenantId.equals(String.valueOf(value))) {
            log.warn("findById 租户不一致已拦截（返回空）：entity={}, id={}, 实体租户={}, 当前租户={}",
                    entity.getClass().getSimpleName(), point.getArgs().length > 0 ? point.getArgs()[0] : "?",
                    value, tenantId);
            return Optional.empty();
        }
        return result;
    }

    /** 租户字段解析（按实体类缓存）：字段名 tenantId 优先，其次 @TenantIdField 标注 */
    static Field resolveTenantField(Class<?> entityClass) {
        return FIELD_CACHE.computeIfAbsent(entityClass, FindByIdTenantAspect::scan);
    }

    private static Field scan(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if ("tenantId".equals(f.getName()) || f.isAnnotationPresent(TenantIdField.class)) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        return null;
    }
}
