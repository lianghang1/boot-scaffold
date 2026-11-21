package com.sfi.scaffold.common.datascope;

import java.util.Collections;
import java.util.Set;

/**
 * 部门子树查询 SPI：框架不碰组织实体，由业务实现「某部门 + 其所有下级」的查询。
 *
 * <p>仅在归约 {@link DataScopeType#DEPT_AND_CHILD} 档时被调用。业务可用递归 CTE、
 * 物化路径（如 ancestor_ids）或缓存的闭包表实现，框架只消费结果集合。</p>
 *
 * @author lianghang
 */
@FunctionalInterface
public interface DeptTreeProvider {

    /**
     * 返回部门及其所有下级部门的 ID 集合（含传入的 deptId 自身）。
     *
     * @param deptId 当前部门 ID
     * @return 部门及子树 ID 集合；查不到时返回空集
     */
    Set<Long> selfAndChildDepts(Long deptId);

    /** 缺省实现：不做任何展开（业务未实现 SPI 时，本档位归约出空部门集）。 */
    static DeptTreeProvider noop() {
        return deptId -> Collections.emptySet();
    }
}
