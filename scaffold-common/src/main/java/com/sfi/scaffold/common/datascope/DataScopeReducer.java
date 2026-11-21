package com.sfi.scaffold.common.datascope;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 多角色数据范围归约工具（对齐芋道 PermissionServiceImpl.getDeptDataPermission 的并集规则）。
 *
 * <p>把一个用户在某租户下的所有角色范围配置，拍平成一个 {@link DataScopeDecision}：
 * 遍历每个角色，按档位往 All / deptIds / Self 三个维度累加（OR 合并，取最宽松）：</p>
 * <ul>
 *   <li>任一角色 {@link DataScopeType#ALL} → all=true，一票通过；</li>
 *   <li>{@link DataScopeType#DEPT_CUSTOM} → 并入角色的指定部门；</li>
 *   <li>{@link DataScopeType#DEPT_ONLY} → 并入当前部门；</li>
 *   <li>{@link DataScopeType#DEPT_AND_CHILD} → 并入当前部门 + 子树（经 {@link DeptTreeProvider}）；</li>
 *   <li>{@link DataScopeType#SELF} → self=true；</li>
 *   <li>无角色 / 角色集合为空 → self=true（fail-closed，绝不放大到全量）。</li>
 * </ul>
 *
 * <p>纯静态、无 Spring 依赖、无副作用，便于单测；当前部门为 null 时部门档自动跳过
 * （不放大权限）。all=true 时最终决策忽略 deptIds 与 self。</p>
 *
 * @author lianghang
 */
public final class DataScopeReducer {

    private DataScopeReducer() {
    }

    /**
     * 归约多角色数据范围。
     *
     * @param scopes         用户所有角色的数据范围配置（可为空）
     * @param currentDeptId  用户在当前租户下的部门 ID（可为 null）
     * @param deptTree       部门子树查询 SPI（仅 DEPT_AND_CHILD 用，可为 null）
     * @return 归约后的单一决策
     */
    public static DataScopeDecision reduce(Collection<RoleDataScope> scopes,
                                           Long currentDeptId,
                                           DeptTreeProvider deptTree) {
        if (scopes == null || scopes.isEmpty()) {
            return DataScopeDecision.self();
        }
        boolean all = false;
        boolean self = false;
        Set<Long> deptIds = new HashSet<>();

        for (RoleDataScope scope : scopes) {
            if (scope == null) {
                continue;
            }
            DataScopeType type = scope.getType();
            if (type == null) {
                continue;
            }
            switch (type) {
                case ALL:
                    all = true;
                    break;
                case DEPT_CUSTOM:
                    deptIds.addAll(scope.getCustomDeptIds());
                    break;
                case DEPT_ONLY:
                    if (currentDeptId != null) {
                        deptIds.add(currentDeptId);
                    }
                    break;
                case DEPT_AND_CHILD:
                    if (currentDeptId != null) {
                        deptIds.add(currentDeptId);
                        if (deptTree != null) {
                            Set<Long> children = deptTree.selfAndChildDepts(currentDeptId);
                            if (children != null) {
                                deptIds.addAll(children);
                            }
                        }
                    }
                    break;
                case SELF:
                    self = true;
                    break;
                default:
                    break;
            }
        }

        // ALL 一票通过：忽略其余维度，避免残留条件误伤
        if (all) {
            return DataScopeDecision.all();
        }
        if (deptIds.isEmpty() && !self) {
            return DataScopeDecision.self();
        }
        return DataScopeDecision.of(deptIds, self);
    }

    /** 便捷重载：不涉及部门子树时使用。 */
    public static DataScopeDecision reduce(Collection<RoleDataScope> scopes, Long currentDeptId) {
        return reduce(scopes, currentDeptId, null);
    }

    /** 便捷重载：无当前部门（平台身份）时使用。 */
    public static DataScopeDecision reduce(Collection<RoleDataScope> scopes) {
        return reduce(scopes, null, null);
    }
}
