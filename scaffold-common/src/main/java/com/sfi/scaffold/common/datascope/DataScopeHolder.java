package com.sfi.scaffold.common.datascope;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 数据范围决策持有器（TTL）：当前请求的数据可见范围，供 jpa 切面与 query 过滤器读取。
 *
 * <p>仿 {@code ScaffoldAuditorHolder} 的单向桥模式：由安全层（starter-security）
 * 在认证成功后从 {@code LoginUser.attributes} 还原决策并写入本持有器，jpa / query
 * 两条过滤通路只读取、不反向依赖 security。放在 common 供各 starter 共用。</p>
 *
 * <p>未写入（null）表示"无数据范围约束"——平台身份 / 系统任务 / 未接入数据权限的场景
 * 全放行；fail-closed 由归约阶段保证（无角色归约为 SELF），执行层不对空值报错，
 * 避免未标注数据权限的普通查询被误伤。</p>
 *
 * <p>请求结束由安全层清理，防线程复用串号；TTL 底座保证异步线程取到提交那一刻的决策。</p>
 *
 * @author lianghang
 */
public final class DataScopeHolder {

    private static final TransmittableThreadLocal<DataScopeDecision> HOLDER = new TransmittableThreadLocal<>();

    private DataScopeHolder() {
    }

    public static void set(DataScopeDecision decision) {
        HOLDER.set(decision);
    }

    public static DataScopeDecision get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
