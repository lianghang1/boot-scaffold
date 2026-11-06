package com.sfi.scaffold.starter.tenant.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 租户过滤豁免标记（黑名单语义的显式逃生门）。
 *
 * <p>两个作用面：
 * <ol>
 *   <li><b>方法/类上</b>：由 {@code TenantIgnoreAspect} 标记线程豁免，标记期间
 *       方法内计划类查询、JPA 查询、findById 兜底切面全部放行——方法返回即恢复，
 *       支持嵌套（按深度计数）；</li>
 *   <li><b>计划类上</b>：{@code TenantParamFilter} 检测到该计划类标注即整体豁免，
 *       该计划所有查询不注入租户条件。</li>
 * </ol>
 *
 * <p>只作用于<b>自动注入的查询</b>：{@code QueryUtilsX.doNativeQuery(sql)} 等纯 SQL
 * 入口本就不注入租户条件，无需也不受本注解影响；平台身份（登录身份无 tenantId）
 * 天然全放行，亦无需标注。</p>
 *
 * @author lianghang
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantIgnore {
}
