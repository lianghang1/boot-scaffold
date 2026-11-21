package com.sfi.scaffold.starter.data.jpa.datascope;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 单个角色的数据范围配置，作为 {@link DataScopeReducer} 的归约输入。
 *
 * <p>框架不假设角色的存储结构（实体/表由业务定义），只约定这一份最小输入：
 * 范围类型 + 指定部门集合（仅 DEPT_CUSTOM 用）。业务把角色配置映射成本对象即可。</p>
 *
 * @author lianghang
 */
public final class RoleDataScope {

    private final DataScopeType type;
    private final Set<Long> customDeptIds;

    public RoleDataScope(DataScopeType type, Collection<Long> customDeptIds) {
        if (type == null) {
            throw new IllegalArgumentException("DataScopeType 不能为空");
        }
        this.type = type;
        this.customDeptIds = customDeptIds == null || customDeptIds.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(customDeptIds));
    }

    public static RoleDataScope of(DataScopeType type) {
        return new RoleDataScope(type, Collections.emptySet());
    }

    public static RoleDataScope of(DataScopeType type, Collection<Long> customDeptIds) {
        return new RoleDataScope(type, customDeptIds);
    }

    public DataScopeType getType() {
        return type;
    }

    public Set<Long> getCustomDeptIds() {
        return customDeptIds;
    }
}
