package com.sfi.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通用 CRUD 资源声明：贴在继承 AbstractCrudController 的 controller 类上，
 * 一次性描述该资源的检索模型、写 DTO、审计模块与各动作权限点。
 *
 * <p>基类模板方法与权限/审计切面都从这里读元数据——把「每个资源都不一样」的
 * 权限串、模块名从方法注解挪到类级声明，使 controller 子类可以零方法。</p>
 *
 * <p>权限串为 any-of 语义（具备任意一个即放行），与 {@code @RequirePermission}
 * 一致；空数组表示该动作不校验权限（如公开查询端点）。动作粒度：
 * {@code list}（列表/分页）、{@code get}（详情）、{@code save}（新增/修改）、
 * {@code delete}（删除）。</p>
 *
 * <pre>{@code
 * @CrudResource(
 *     entity = SysDictData.class,
 *     dto = DictDataRequest.class,
 *     module = "字典管理",
 *     listPerm = "platform:dict:list",
 *     savePerm = "platform:dict:save"
 * )
 * @RestController
 * @RequestMapping("/api/dict-data")
 * public class DictDataController extends AbstractCrudController<...> { }
 * }</pre>
 *
 * @author lianghang
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CrudResource {

    /** 检索/持久化实体（JPA 实体，通路②默认检索模型）。 */
    Class<?> entity();

    /** 写操作请求 DTO（save 用，须可 Jackson 反序列化）。 */
    Class<?> dto();

    /** 审计模块名（@OperateLog.module）。 */
    String module() default "";

    /** 列表/分页权限点（any-of）。 */
    String[] listPerm() default {};

    /** 详情权限点（any-of）；留空时回退到 listPerm。 */
    String[] getPerm() default {};

    /** 新增/修改权限点（any-of）。 */
    String[] savePerm() default {};

    /** 删除权限点（any-of）；留空时回退到 savePerm。 */
    String[] deletePerm() default {};
}
