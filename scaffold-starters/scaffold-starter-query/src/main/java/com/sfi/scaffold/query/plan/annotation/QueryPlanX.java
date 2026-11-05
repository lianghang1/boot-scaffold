package com.sfi.scaffold.query.plan.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 查询计划端点声明。
 *
 * <p>标注在 controller（类或方法）上，声明该端点允许执行的查询计划类。
 * 前端传入计划类 SimpleName（如 {@code UserPlan}），业务在端点内经
 * {@code QueryUtilsX.resolvePlan(...)} 匹配到计划类后按类执行：
 *
 * <pre>{@code
 * @QueryPlanX({UserPlan.class, OrderPlan.class})
 * @RestController
 * @RequestMapping("/api/query")
 * public class QueryController {
 *
 *     @PostMapping("/page")
 *     public Page<?> page(@RequestParam String plan, @RequestBody Map<String, String> params) {
 *         Class<?> planClass = QueryUtilsX.resolvePlan(this, plan);
 *         return QueryUtilsX.doQuery(QueryUtilsX.parse(params), PageRequest.of(0, 15), planClass);
 *     }
 * }
 * }</pre>
 *
 * <p>计划类自身用 {@link TableX} / {@link JoinTableX} / {@link ColumnX}
 * 声明联表结构与字段映射，计划类即结果类（字段即查询结果字段）。
 * 注解即权限白名单，未在 value 中的计划类不可被该端点执行。
 *
 * @author lianghang
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueryPlanX {

    /** 允许执行的查询计划类，前端按类 SimpleName 匹配 */
    Class<?>[] value() default {};
}
