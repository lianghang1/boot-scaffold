package com.sfi.scaffold.starter.data.jpa.datascope;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多角色数据范围归约单测：覆盖芋道并集规则的全部关键分支。
 *
 * @author lianghang
 */
class DataScopeReducerTest {

    /** 模拟部门子树：10 的下级是 11、12。 */
    private final DeptTreeProvider deptTree = deptId -> {
        if (deptId == null || deptId != 10L) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(10L, 11L, 12L));
    };

    @Test
    void noRoleShouldFallbackToSelf() {
        DataScopeDecision decision = DataScopeReducer.reduce(null);
        assertThat(decision.isSelf()).isTrue();
        assertThat(decision.isAll()).isFalse();
        assertThat(decision.getDeptIds()).isEmpty();

        assertThat(DataScopeReducer.reduce(Collections.emptyList()).isSelf()).isTrue();
    }

    @Test
    void anyAllRoleShouldWin() {
        DataScopeDecision decision = DataScopeReducer.reduce(Arrays.asList(
                RoleDataScope.of(DataScopeType.SELF),
                RoleDataScope.of(DataScopeType.ALL),
                RoleDataScope.of(DataScopeType.DEPT_CUSTOM, Collections.singleton(7L))
        ));
        assertThat(decision.isAll()).isTrue();
        assertThat(decision.isSelf()).isFalse();
        assertThat(decision.getDeptIds()).isEmpty();
    }

    @Test
    void selfRolesShouldMerge() {
        DataScopeDecision decision = DataScopeReducer.reduce(Arrays.asList(
                RoleDataScope.of(DataScopeType.SELF),
                RoleDataScope.of(DataScopeType.SELF)
        ));
        assertThat(decision.isSelf()).isTrue();
        assertThat(decision.isAll()).isFalse();
    }

    @Test
    void customDeptRolesShouldUnion() {
        DataScopeDecision decision = DataScopeReducer.reduce(Arrays.asList(
                RoleDataScope.of(DataScopeType.DEPT_CUSTOM, Arrays.asList(1L, 2L)),
                RoleDataScope.of(DataScopeType.DEPT_CUSTOM, Arrays.asList(2L, 3L))
        ));
        assertThat(decision.getDeptIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(decision.isSelf()).isFalse();
    }

    @Test
    void deptOnlyShouldUseCurrentDept() {
        DataScopeDecision decision = DataScopeReducer.reduce(
                Collections.singletonList(RoleDataScope.of(DataScopeType.DEPT_ONLY)), 10L);
        assertThat(decision.getDeptIds()).containsExactly(10L);
    }

    @Test
    void deptOnlyWithoutCurrentDeptShouldFallbackToSelf() {
        DataScopeDecision decision = DataScopeReducer.reduce(
                Collections.singletonList(RoleDataScope.of(DataScopeType.DEPT_ONLY)), null);
        // 部门档无法定位部门 → 不放全量，兜底仅本人
        assertThat(decision.isSelf()).isTrue();
        assertThat(decision.getDeptIds()).isEmpty();
    }

    @Test
    void deptAndChildShouldExpandSubtree() {
        DataScopeDecision decision = DataScopeReducer.reduce(
                Collections.singletonList(RoleDataScope.of(DataScopeType.DEPT_AND_CHILD)),
                10L, deptTree);
        Set<Long> ids = decision.getDeptIds();
        assertThat(ids).containsExactlyInAnyOrder(10L, 11L, 12L);
    }

    @Test
    void deptAndChildWithoutProviderShouldOnlyIncludeSelfDept() {
        DataScopeDecision decision = DataScopeReducer.reduce(
                Collections.singletonList(RoleDataScope.of(DataScopeType.DEPT_AND_CHILD)), 10L);
        // 无 SPI 时只放当前部门，不放大到子树
        assertThat(decision.getDeptIds()).containsExactly(10L);
    }

    @Test
    void deptAndSelfShouldMergeAsOr() {
        DataScopeDecision decision = DataScopeReducer.reduce(Arrays.asList(
                RoleDataScope.of(DataScopeType.DEPT_CUSTOM, Collections.singleton(5L)),
                RoleDataScope.of(DataScopeType.SELF)
        ), null);
        assertThat(decision.getDeptIds()).containsExactly(5L);
        assertThat(decision.isSelf()).isTrue();
    }

    @Test
    void mixedRolesShouldUnionAllDimensions() {
        DataScopeDecision decision = DataScopeReducer.reduce(Arrays.asList(
                RoleDataScope.of(DataScopeType.DEPT_CUSTOM, Collections.singleton(1L)),
                RoleDataScope.of(DataScopeType.DEPT_ONLY),
                RoleDataScope.of(DataScopeType.DEPT_AND_CHILD),
                RoleDataScope.of(DataScopeType.SELF)
        ), 10L, deptTree);
        assertThat(decision.getDeptIds()).containsExactlyInAnyOrder(1L, 10L, 11L, 12L);
        assertThat(decision.isSelf()).isTrue();
        assertThat(decision.isAll()).isFalse();
    }

    @Test
    void decisionShouldBeImmutable() {
        Set<Long> source = new HashSet<>(Collections.singleton(1L));
        DataScopeDecision decision = DataScopeDecision.ofDeptIds(source);
        source.add(2L);
        assertThat(decision.getDeptIds()).containsExactly(1L);
    }

    @Test
    void typeShouldParseAndValidateCodes() {
        assertThat(DataScopeType.CODES).containsExactly(
                "ALL", "DEPT_CUSTOM", "DEPT_ONLY", "DEPT_AND_CHILD", "SELF");
        assertThat(DataScopeType.of(" dept_only ")).isEqualTo(DataScopeType.DEPT_ONLY);
        assertThat(DataScopeType.isValid("self")).isTrue();
        assertThat(DataScopeType.isValid("DEPT_CHILD")).isFalse();
        assertThat(DataScopeType.isValid(null)).isFalse();
    }
}
