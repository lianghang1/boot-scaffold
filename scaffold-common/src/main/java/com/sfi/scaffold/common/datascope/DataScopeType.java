package com.sfi.scaffold.common.datascope;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据权限范围类型（对齐芋道 ruoyi-vue-pro 的五档模型）。
 *
 * <p>放在 common 供 jpa 切面与 query 检索过滤器共用——二者分属不同 starter，
 * 不能互相依赖，故把纯能力下沉到最底层。</p>
 *
 * <p>ALL（全部）不拼任何条件；SELF（仅本人，按 create_by 匹配）；部门三档
 * （DEPT_CUSTOM / DEPT_ONLY / DEPT_AND_CHILD）经 {@link DataScopeReducer}
 * 归约成 {@link DataScopeDecision}，再交给执行层启用 dept 过滤。</p>
 *
 * <p>多角色取并集（最宽松）：任一角色 ALL 即全部可见；部门范围累加；
 * SELF 单独标记。无角色兜底为 SELF，fail-closed 不放全量。</p>
 *
 * @author lianghang
 */
public enum DataScopeType {

    /** 全部数据：不拼任何过滤条件。 */
    ALL,

    /** 指定部门：仅可见显式授权的部门集合。 */
    DEPT_CUSTOM,

    /** 本部门：仅当前用户所属部门。 */
    DEPT_ONLY,

    /** 本部门及以下：当前部门 + 递归子部门（子树由 DeptTreeProvider 提供）。 */
    DEPT_AND_CHILD,

    /** 仅本人：按 create_by 匹配当前审计人。 */
    SELF;

    /** 全部合法档位编码（不可变），供宿主做入参校验。 */
    public static final Set<String> CODES;

    static {
        Set<String> codes = new LinkedHashSet<>();
        for (DataScopeType type : values()) {
            codes.add(type.name());
        }
        CODES = Collections.unmodifiableSet(codes);
    }

    /** 字符串转枚举；非法值抛 IllegalArgumentException。 */
    public static DataScopeType of(String code) {
        if (code == null) {
            throw new IllegalArgumentException("数据范围不能为空，可选值：" + CODES);
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("数据范围非法：" + code + "，可选值：" + CODES, e);
        }
    }

    /** 是否为合法档位编码（大小写不敏感）。 */
    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        return CODES.contains(code.trim().toUpperCase());
    }
}
