package com.sfi.scaffold.query.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 逻辑删除过滤豁免标记：标注后，方法/类内的 query 检索不注入 {@code deleted=false}，
 * 从而可查到已逻辑删除的数据（回收站、审计追溯、历史查看）。
 *
 * <p>与 {@code @TenantIgnore} 完全独立、可单独使用也可叠加：本注解只放行逻辑删除过滤，
 * 不触碰租户隔离——因此"某租户的回收站"可以只看本租户的已删除数据，不会越权。</p>
 *
 * <p>作用面：方法/类上由 {@code IncludeDeletedAspect} 标记线程豁免，方法返回即恢复，
 * 支持嵌套（按深度计数）。只作用于 query 通路自动注入的 {@code deleted=false}；
 * 单条按 id 查询（{@code BaseService.get}）本身不做全局过滤，不受本注解影响。</p>
 *
 * <p><b>前端无法通过在请求参数里传 deleted 达到同样效果</b>——过滤器固定覆盖同名字段，
 * 想看已删除数据只能由服务端显式标注本注解。</p>
 *
 * @author lianghang
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IncludeDeleted {
}
