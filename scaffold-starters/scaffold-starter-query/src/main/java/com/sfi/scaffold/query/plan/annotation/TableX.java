package com.sfi.scaffold.query.plan.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 查询计划主表声明。
 *
 * @author lianghang
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TableX {

    /** 表名 */
    String name();

    /** 别名（字段映射默认前缀） */
    String alias() default "";

    /** 主键字段名 */
    String pkColumn() default "id";

    /** 强制索引名称（MySQL 语法：表名后拼 FORCE INDEX(idx)） */
    String useIndex() default "";

    /**
     * 具名数据源（与宿主注册的 {@code NamedDataSource} Bean 名对应，多库检索用）。
     * 空串表示不绑定：走默认数据源或调用方 {@code @TargetDataSource} 的 ThreadLocal 路由。
     */
    String dataSource() default "";

    /** 固定过滤条件（总是生效，前端无法覆盖） */
    FixedFilter[] filters() default {};
}
