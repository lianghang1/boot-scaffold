package com.sfi.scaffold.starter.data.jpa.service;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.common.exception.GlobalErrorCode;
import com.sfi.scaffold.common.request.PageParam;
import com.sfi.scaffold.common.result.PageResult;
import com.sfi.scaffold.query.QueryUtilsX;
import com.sfi.scaffold.query.filter.QueryFilterX;
import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;
import com.sfi.scaffold.starter.data.jpa.logicdelete.DeletedFieldAccessor;
import org.springframework.core.ResolvableType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

import java.util.List;

/**
 * 通用 CRUD 服务基类。
 *
 * <p>收口所有 {@link BaseEntity} 子类共享的骨架：按 id 查询（查不到返回 null）、
 * 存在性判定、列表/分页/计数、逻辑删除。逻辑删除/租户的查询过滤统一由切面按
 * 字段名探测自动拼接，基类不硬编码这些字段；写侧按字段名反射打删除标记。
 * 业务差异（加密、平台兜底、唯一键校验）由子类覆盖或另起方法——基类只保证
 * 「每张表都要写的那几行」只写一次。</p>
 *
 * <p><b>泛型与 Repository 对齐</b>：所有实体主键为 Long（{@link BaseEntity}），
 * Repository 类型固定 {@code JpaRepository<E, Long>}，子类构造时把具体 Repository
 * 传给 {@code super(...)}。列表/分页的实体类型由 {@link ResolvableType} 从子类
 * 声明的泛型实参解析，调用方无需传 beanClass。</p>
 *
 * <p><b>查询分工</b>：按 id 单条走 JPA {@code findById}；列表/分页/计数走
 * {@link QueryUtilsX}（query 通路，支持 QueryFilterX 条件、plan 计划、全局
 * deleted/租户过滤），与单条查询互补，不在一次查询里混用通路。</p>
 *
 * @param <E> 实体类型（继承 BaseEntity）
 * @author lianghang
 */
public abstract class BaseService<E extends BaseEntity> {

    private final JpaRepository<E, Long> repository;
    private volatile Class<E> entityClass;

    protected BaseService(JpaRepository<E, Long> repository) {
        this.repository = repository;
    }

    /** 暴露 Repository 给子类写差异化查询，避免子类重复持有同一实例。 */
    protected JpaRepository<E, Long> repo() {
        return repository;
    }

    /**
     * 解析子类声明的实体泛型实参（CGLIB 代理下取用户类）。
     */
    @SuppressWarnings("unchecked")
    protected Class<E> entityClass() {
        Class<E> cached = entityClass;
        if (cached == null) {
            synchronized (this) {
                cached = entityClass;
                if (cached == null) {
                    Class<?> userClass = ClassUtils.getUserClass(getClass());
                    Class<?> resolved = ResolvableType.forClass(userClass)
                            .as(BaseService.class).getGeneric(0).resolve();
                    if (resolved == null) {
                        throw new IllegalStateException(
                                userClass.getSimpleName() + " 未能解析 BaseService 实体泛型实参");
                    }
                    entityClass = cached = (Class<E>) resolved;
                }
            }
        }
        return cached;
    }

    /**
     * 按 id 查询，自动过滤逻辑删除记录；不存在返回 null（唯一公共查询入口，不抛异常）。
     * deleted 字段由实体自行声明，此处按字段名反射探测（非 BaseEntity 硬编码），
     * 与 findById 切面、query 过滤器三处口径一致，形成双保险。
     */
    public E get(Long id) {
        if (id == null) {
            return null;
        }
        return repository.findById(id)
                .filter(e -> !DeletedFieldAccessor.isDeleted(e))
                .orElse(null);
    }

    /**
     * 写操作内部用：不存在抛 NOT_FOUND，供 delete 及子类 save 装配使用。
     * 与公共查询入口 get 区分，避免"查询不到报错"污染读语义。
     */
    protected E require(Long id) {
        E entity = get(id);
        if (entity == null) {
            throw BizException.of(GlobalErrorCode.NOT_FOUND, "记录不存在：{}", id);
        }
        return entity;
    }

    /** 记录是否存在（未逻辑删除）。 */
    public boolean exists(Long id) {
        return get(id) != null;
    }

    /** 保存（新增或更新）——通用入口，子类可覆盖加业务校验。 */
    @Transactional(rollbackFor = Exception.class)
    public E save(E entity) {
        return repository.save(entity);
    }

    /**
     * 逻辑删除：按字段名反射打 deleted 标记 + 保存，不物理删除。不存在抛 NOT_FOUND。
     * 字段由实体自行声明（非 BaseEntity），反射写入与租户的字段探测同构。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        E entity = require(id);
        DeletedFieldAccessor.markDeleted(entity);
        repository.save(entity);
    }

    // ==================== 列表 / 分页 / 计数（query 通路） ====================

    /** 条件列表（不分页，受 query maxRows 上限保护）。 */
    public List<E> list(List<QueryFilterX> filters) {
        return QueryUtilsX.doListQuery(filters, entityClass());
    }

    /** 条件列表 + 排序。 */
    public List<E> list(List<QueryFilterX> filters, Sort sort) {
        return QueryUtilsX.doListQuery(filters, sort, entityClass());
    }

    /** 条件计数。 */
    public long count(List<QueryFilterX> filters) {
        return QueryUtilsX.doCount(filters, entityClass());
    }

    /**
     * 条件分页（1 基，返回统一 {@link PageResult}；pageSize=-1 表示不分页拉全量）。
     */
    public PageResult<E> page(List<QueryFilterX> filters, PageParam pageParam) {
        return QueryUtilsX.queryPage(filters, pageParam, entityClass());
    }
}
