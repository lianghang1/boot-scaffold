package com.sfi.scaffold.query.plan.annotation;

import com.sfi.scaffold.query.filter.OperatorX;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 固定过滤条件注解（name/op/value/type）。
 *
 * <p>用于 {@link TableX#filters()}，声明的条件总是生效，前端无法覆盖。
 * 注意：引用的字段必须在计划类中有 {@link ColumnX} 映射。
 *
 * @author lianghang
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FixedFilter {

    /** 属性名（计划类字段名） */
    String name();

    /** 运算符 */
    OperatorX op() default OperatorX.EQ;

    /** 固定值（字符串，按 type 解析） */
    String value() default "";

    /** 值类型 */
    Class<?> type() default String.class;
}
