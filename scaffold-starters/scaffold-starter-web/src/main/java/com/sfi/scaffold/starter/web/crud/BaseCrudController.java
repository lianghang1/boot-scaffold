package com.sfi.scaffold.starter.web.crud;

import com.sfi.scaffold.common.request.IdRequest;
import com.sfi.scaffold.common.request.PageParam;
import com.sfi.scaffold.common.result.PageResult;
import com.sfi.scaffold.common.result.R;
import com.sfi.scaffold.common.util.JsonUtils;
import com.sfi.scaffold.query.QueryUtilsX;
import com.sfi.scaffold.query.filter.QueryFilterX;
import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;
import com.sfi.scaffold.starter.data.jpa.service.BaseService;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

/**
 * 通用单表 CRUD 控制层基类——只固定 HTTP 契约与三处编排，业务差异靠子类覆盖少量方法。
 *
 * <p><b>它做什么</b>：固定四个端点（list/get/save/delete）的映射、参数绑定与返回包装，
 * 并把请求翻译成 {@link BaseService} 调用。一个纯单表资源接入时只需：Service 继承
 * {@code BaseService}，Controller 继承本类并在构造器把 Service 与实体 Class 传给 super，
 * 四个端点即自动生成。</p>
 *
 * <p><b>它不做什么</b>：权限（交给 URL 权限拦截）、审计、业务规则、字段映射差异、
 * plan 白名单以外的判断——这些都不进基类。写操作带业务规则（加密/兜底/复杂装配）
 * 的资源不接本基类，直接手写 Controller。</p>
 *
 * <p><b>四处可覆盖点</b>（默认实现即通用行为）：</p>
 * <ol>
 *   <li>{@link #filtersOf(HttpServletRequest)}：请求参数 → QueryFilterX 集合，子类可增删改条件；</li>
 *   <li>{@link #rewriteList(PageResult)}：列表结果整体改写（脱敏、字段加工、转 VO、改 total/过滤），默认原样；</li>
 *   <li>{@link #toEntity(Object)}：DTO → 实体，子类可改字段装配；</li>
 *   <li>{@link #beforeDelete(Long)}：删除前钩子，子类可做校验/联动。</li>
 * </ol>
 *
 * <p><b>plan</b>：{@code /list} 走子类 Service 的默认实体；{@code /list/{plan}} 经
 * {@link QueryUtilsX#resolvePlan} 在 {@code @QueryPlanX} 白名单内解析检索模型，
 * 两者全程自洽、不在一次查询里混用通路。</p>
 *
 * @param <E> 实体（继承 BaseEntity）
 * @param <D> 写请求 DTO
 * @param <S> 业务服务（继承 BaseService）
 * @author lianghang
 */
public abstract class BaseCrudController<E extends BaseEntity, D, S extends BaseService<E>> {

    protected final S service;
    protected final Class<E> entityClass;

    protected BaseCrudController(S service, Class<E> entityClass) {
        this.service = service;
        this.entityClass = entityClass;
    }

    /** 列表/分页：默认查子类实体；带 plan 时查白名单检索模型。 */
    @GetMapping("/list")
    public R<PageResult<?>> list(HttpServletRequest request) {
        return R.ok(doList(null, request));
    }

    /** 按 plan 列表：plan 只能是 @QueryPlanX 白名单内的类 SimpleName。 */
    @GetMapping("/list/{plan}")
    public R<PageResult<?>> listByPlan(@PathVariable String plan, HttpServletRequest request) {
        return R.ok(doList(plan, request));
    }

    private PageResult<?> doList(String plan, HttpServletRequest request) {
        List<QueryFilterX> filters = filtersOf(request);
        PageParam page = PageQuerySupport.pageParamOf(request);
        PageResult<?> result;
        if (plan == null || plan.isEmpty()) {
            result = service.page(filters, page);
        } else {
            Class<?> target = QueryUtilsX.resolvePlan(ClassUtils.getUserClass(getClass()), plan);
            result = PageQuerySupport.query(filters, page, target);
        }
        return rewriteList(result);
    }

    /** 详情：逻辑删除过滤由 BaseService 保证，查不到返回 null。返回 R<?> 以便子类经 detailOf 转 VO。 */
    @GetMapping("/get")
    public R<?> get(@RequestParam Long id) {
        E entity = service.get(id);
        return R.ok(entity == null ? null : detailOf(entity));
    }

    /** 新增/修改：DTO → 实体 → 通用 save；业务差异在 Service 子类覆盖。 */
    @PostMapping("/save")
    public R<E> save(@Valid @RequestBody D dto) {
        return R.ok(service.save(toEntity(dto)));
    }

    /** 删除：删除前钩子 → 逻辑删除。 */
    @PostMapping("/delete")
    public R<Void> delete(@Valid @RequestBody IdRequest req) {
        beforeDelete(req.getId());
        service.delete(req.getId());
        return R.ok();
    }

    // ==================== 四处可覆盖点 ====================

    /** 请求参数 → 查询条件；子类可在 super 结果上增删改（如注入固定条件、改写字段值）。 */
    protected List<QueryFilterX> filtersOf(HttpServletRequest request) {
        return QueryUtilsX.parse(request.getParameterMap());
    }

    /**
     * 列表结果整体改写：脱敏、字段加工、实体转 VO、改 total、过滤某些行等；默认原样返回。
     *
     * <p>入参是已经分页好的 {@link PageResult}（无 plan 走 Service、带 plan 走 query 通路），
     * 子类可直接改其 list/total 后返回，也可 {@code PageResult.of(...)} 构造一个全新结果。</p>
     */
    protected PageResult<?> rewriteList(PageResult<?> result) {
        return result;
    }

    /** DTO → 实体；子类可覆盖做字段级装配。 */
    protected E toEntity(D dto) {
        return JsonUtils.convertValue(dto, entityClass);
    }

    /** 详情出口转换；默认原样返回实体，子类可覆盖成 VO（如敏感字段加密）。 */
    protected Object detailOf(E entity) {
        return entity;
    }

    /** 删除前钩子；默认空实现。 */
    protected void beforeDelete(Long id) {
    }

    /** 供子类读取自身实体泛型（当前实现实体 Class 由构造器传入，保留此工具备查）。 */
    @SuppressWarnings("unchecked")
    protected Class<E> resolveEntityGeneric() {
        Class<?> userClass = ClassUtils.getUserClass(getClass());
        Class<?> resolved = ResolvableType.forClass(userClass)
                .as(BaseCrudController.class).getGeneric(0).resolve();
        return resolved == null ? entityClass : (Class<E>) resolved;
    }
}
