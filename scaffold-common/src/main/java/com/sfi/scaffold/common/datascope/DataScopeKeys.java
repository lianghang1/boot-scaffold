package com.sfi.scaffold.common.datascope;

/**
 * 数据范围在登录身份 / 会话中的传递约定。
 *
 * <p>归约结果 {@link DataScopeDecision} 以 JSON 串存在 {@code LoginUser.attributes}
 * 的这个键下，随 Redis 会话透传；jpa 切面与 query 过滤器据此还原决策并执行过滤。
 * 常量放 common，避免 security / data-jpa / query 各自定义造成漂移。</p>
 *
 * @author lianghang
 */
public final class DataScopeKeys {

    /** attributes 键：数据范围归约结果（JSON 串） */
    public static final String ATTR_DECISION = "dataScope";

    private DataScopeKeys() {
    }
}
