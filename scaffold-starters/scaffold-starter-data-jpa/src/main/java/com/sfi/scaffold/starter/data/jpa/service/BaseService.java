package com.sfi.scaffold.starter.data.jpa.service;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.common.exception.GlobalErrorCode;
import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通用 CRUD 服务基类。
 *
 * <p>收口所有 {@link BaseEntity} 子类共享的骨架方法：逻辑删除过滤的按 id 查询、
 * 逻辑删除、存在性判定。业务差异（加密、平台兜底、唯一键校验）由子类覆盖
 * 或另起方法，不塞进基类——基类只保证「每张表都要写的那几行」只写一次。</p>
 *
 * <p>泛型与 Repository 对齐：所有实体主键为 Long（{@link BaseEntity}），
 * 故 Repository 类型为 {@code JpaRepository<E, Long>}，子类构造时把具体
 * Repository 传给 {@code super(...)} 即可。</p>
 *
 * <p>删除一律走 {@code markDeleted()} + {@code save()} 的逻辑删除，
 * 绝不调用 {@link JpaRepository#delete(Object)} 物理删除。</p>
 *
 * @param <E> 实体类型（继承 BaseEntity）
 * @author lianghang
 */
public abstract class BaseService<E extends BaseEntity> {

    private final JpaRepository<E, Long> repository;

    protected BaseService(JpaRepository<E, Long> repository) {
        this.repository = repository;
    }

    /** 暴露 Repository 给子类写差异化查询，避免子类重复持有同一实例。 */
    protected JpaRepository<E, Long> repo() {
        return repository;
    }

    /**
     * 按 id 查询，自动过滤逻辑删除记录；不存在抛 {@code NOT_FOUND}。
     */
    public E get(Long id) {
        if (id == null) {
            throw BizException.of(GlobalErrorCode.BAD_REQUEST, "id 不能为空");
        }
        return repository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getDeleted()))
                .orElseThrow(() -> BizException.of(GlobalErrorCode.NOT_FOUND, "记录不存在：{}", id));
    }

    /**
     * 按 id 查询，不存在返回 null（不抛异常），供可选装配场景使用。
     */
    public E find(Long id) {
        if (id == null) {
            return null;
        }
        return repository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getDeleted()))
                .orElse(null);
    }

    /** 记录是否存在（未逻辑删除）。 */
    public boolean exists(Long id) {
        return find(id) != null;
    }

    /** 保存（新增或更新）——通用入口，子类可覆盖加业务校验。 */
    @Transactional(rollbackFor = Exception.class)
    public E saveEntity(E entity) {
        return repository.save(entity);
    }

    /**
     * 逻辑删除：标记 deleted + 保存，不物理删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        E entity = get(id);
        entity.markDeleted();
        repository.save(entity);
    }
}
