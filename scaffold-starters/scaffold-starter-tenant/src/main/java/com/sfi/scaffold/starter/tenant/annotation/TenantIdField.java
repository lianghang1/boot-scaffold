package com.sfi.scaffold.starter.tenant.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 实体租户字段标记：字段名不叫 {@code tenantId} 时，标注在承载租户 ID 的字段上，
 * 供 findById 兜底切面定位取值比对。
 *
 * <p>字段名为 {@code tenantId} 的实体无需标注（自动探测）。值类型不约束，
 * 比对时按字符串化比较（与 {@code LoginUser.tenantId} 的 String 承载对齐）。</p>
 *
 * @author lianghang
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantIdField {
}
