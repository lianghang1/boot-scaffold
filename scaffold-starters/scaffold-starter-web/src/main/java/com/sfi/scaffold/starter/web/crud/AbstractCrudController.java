package com.sfi.scaffold.starter.web.crud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfi.scaffold.common.annotation.CrudResource;
import com.sfi.scaffold.common.request.IdRequest;
import com.sfi.scaffold.common.result.PageResult;
import com.sfi.scaffold.common.result.R;
import com.sfi.scaffold.query.QuerySupport;
import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;
import com.sfi.scaffold.starter.data.jpa.service.BaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 通用 CRUD 控制层基类——把「每张管理表都要写的」list/get/save/delete 四个端点
 * 收进基类，controller 子类只需继承并贴 {@link CrudResource} 声明资源元数据，
 * 方法体一行不写。
 *
 * <p>四点约定：</p>
 * <ol>
 *   <li>检索模型、写 DTO、审计模块、各动作权限全部从 {@link CrudResource} 读取；</li>
 *   <li>list 走 {@link QuerySupport}：无 plan 查默认实体、有 plan 匹配白名单计划，
 *       统一 1 基分页，返回 {@link PageResult}；</li>
 *   <li>save 用 Jackson 把 {@code @RequestBody D} 转成 {@code E} 后调
 *       {@link BaseService#save(BaseEntity)}——泛型实参由 Spring 的
 *       {@code ResolvableType} 从子类声明解析，{@code @Valid} 照常生效；</li>
 *   <li>delete 走 {@link BaseService#delete(Long)} 逻辑删除。</li>
 * </ol>
 *
 * <p>权限由 PermissionInterceptor、审计由 OperateLogAspect 读同一份
 * {@link CrudResource} 裁决，基类方法本身不写死任何权限/审计注解，避免
 * 所有子类共用一个权限点。</p>
 *
 * @param <E> 实体（继承 BaseEntity）
 * @param <D> 写请求 DTO
 * @param <S> 业务服务（继承 BaseService）
 * @author lianghang
 */
public abstract class AbstractCrudController<E extends BaseEntity, D, S extends BaseService<E>> {

    protected final S service;
    protected final QuerySupport querySupport;
    protected final ObjectMapper objectMapper;

    protected AbstractCrudController(S service, QuerySupport querySupport, ObjectMapper objectMapper) {
        this.service = service;
        this.querySupport = querySupport;
        this.objectMapper = objectMapper;
    }

    /** 列表/分页：QuerySupport 统一派发，全链路 1 基。 */
    @GetMapping("/list")
    public R<PageResult<E>> list(HttpServletRequest request) {
        return R.ok(querySupport.query(this, request.getParameterMap(), entityClass()));
    }

    /** 详情：按 id 查询（逻辑删除过滤由 BaseService 保证，查不到返回 null）。 */
    @GetMapping("/get")
    public R<E> get(@RequestParam Long id) {
        return R.ok(service.get(id));
    }

    /** 新增/修改：DTO → 实体 → 通用 save；加密/唯一键等业务差异在 Service 子类覆盖。 */
    @PostMapping
    public R<E> save(@Valid @RequestBody D dto) {
        return R.ok(service.save(toEntity(dto)));
    }

    /** 删除：逻辑删除。 */
    @PostMapping("/delete")
    public R<Void> delete(@Valid @RequestBody IdRequest req) {
        service.delete(req.getId());
        return R.ok();
    }

    /** DTO → 实体；子类可覆盖做字段级映射（如敏感字段装配）。 */
    protected E toEntity(D dto) {
        return objectMapper.convertValue(dto, entityClass());
    }

    /** 从类级 @CrudResource 读实体类型——避免反射解析泛型实参的脆弱性。 */
    @SuppressWarnings("unchecked")
    protected Class<E> entityClass() {
        CrudResource cr = this.getClass().getAnnotation(CrudResource.class);
        if (cr == null) {
            throw new IllegalStateException(
                    this.getClass().getSimpleName() + " 继承 AbstractCrudController 必须声明 @CrudResource");
        }
        return (Class<E>) cr.entity();
    }
}
