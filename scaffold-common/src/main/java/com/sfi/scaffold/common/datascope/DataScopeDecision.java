package com.sfi.scaffold.common.datascope;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 数据范围归约结果：把一个用户的多个角色范围拍平成三个字段，供过滤执行层使用。
 *
 * <p>对齐芋道 DeptDataPermissionRespDTO 的模型：</p>
 * <ul>
 *   <li>{@code all=true} —— 全部可见，不拼任何条件（一票通过）；</li>
 *   <li>{@code deptIds} —— 可见部门集合，非空时业务表按 dept_id IN (...) 过滤；</li>
 *   <li>{@code self=true} —— 仅本人，业务表按 create_by = 当前审计人过滤。</li>
 * </ul>
 *
 * <p>deptIds 与 self 为 OR 关系（各自命中即可见）；all 为 true 时二者被忽略。
 * 对象不可变，由 {@link DataScopeReducer} 产出。可 JSON 序列化/反序列化
 * （随 LoginUser.attributes 会话透传，键见 {@code DataScopeKeys}）。</p>
 *
 * @author lianghang
 */
public final class DataScopeDecision {

    private final boolean all;
    private final Set<Long> deptIds;
    private final boolean self;

    private DataScopeDecision(boolean all, Collection<Long> deptIds, boolean self) {
        this.all = all;
        this.deptIds = deptIds == null || deptIds.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(deptIds));
        this.self = self;
    }

    /** 全部可见（all 一票通过，忽略部门与本人条件）。 */
    public static DataScopeDecision all() {
        return new DataScopeDecision(true, Collections.emptySet(), false);
    }

    /** 仅本人可见（无角色时的 fail-closed 兜底）。 */
    public static DataScopeDecision self() {
        return new DataScopeDecision(false, Collections.emptySet(), true);
    }

    /** 指定部门可见。 */
    public static DataScopeDecision ofDeptIds(Collection<Long> deptIds) {
        return new DataScopeDecision(false, deptIds, false);
    }

    /** 组合部门集合与本人条件（OR）。 */
    public static DataScopeDecision of(Collection<Long> deptIds, boolean self) {
        return new DataScopeDecision(false, deptIds, self);
    }

    /** JSON 反序列化入口（会话读回时还原）。 */
    @JsonCreator
    public static DataScopeDecision deserialize(@JsonProperty("all") boolean all,
                                                @JsonProperty("deptIds") Collection<Long> deptIds,
                                                @JsonProperty("self") boolean self) {
        if (all) {
            return all();
        }
        return new DataScopeDecision(false, deptIds, self);
    }

    public boolean isAll() {
        return all;
    }

    public Set<Long> getDeptIds() {
        return deptIds;
    }

    public boolean isSelf() {
        return self;
    }

    /** 是否无需任何过滤（全部可见）。 */
    public boolean isUnrestricted() {
        return all;
    }

    @Override
    public String toString() {
        return "DataScopeDecision{all=" + all + ", deptIds=" + deptIds + ", self=" + self + '}';
    }
}
