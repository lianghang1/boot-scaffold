package com.sfi.scaffold.starter.data.jpa.logicdelete;

import com.sfi.scaffold.query.core.IncludeDeletedHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.util.Optional;

/**
 * findById 逻辑删除兜底切面：按主键直取绕过 Hibernate Filter（Hibernate 5.6 硬伤），
 * 由本切面在结果上比对 deleted 字段——已删除返回空，语义与「查不到数据」统一。
 *
 * <p>字段定位按固定字段名 {@code deleted} 反射探测（与 query/写侧一致），无该字段
 * 视为非逻辑删除实体原样放行。{@code @IncludeDeleted} 豁免期间不比对。</p>
 *
 * <p>与租户 findById 兜底切面的差异：逻辑删除不依赖登录态，全局生效；
 * 无租户比对时的 WARN 审计（删除态是正常业务状态，不记警告）。</p>
 *
 * @author lianghang
 */
@Aspect
public class FindByIdDeletedAspect {

    // 注意：CrudRepository<ID> 泛型无界，findById 擦除后参数为 Object，
    // 故用 (*) 匹配任意单参 findById（含业务自定义重载）
    @Pointcut("execution(public java.util.Optional org.springframework.data.repository.CrudRepository+.findById(*))")
    public void findByIdCall() {
    }

    @Around("findByIdCall()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Object result = point.proceed();
        if (!(result instanceof Optional) || !((Optional<?>) result).isPresent()) {
            return result;
        }
        if (IncludeDeletedHolder.isMarked()) {
            return result;
        }
        Object entity = ((Optional<?>) result).get();
        if (DeletedFieldAccessor.isDeleted(entity)) {
            return Optional.empty();
        }
        return result;
    }
}
