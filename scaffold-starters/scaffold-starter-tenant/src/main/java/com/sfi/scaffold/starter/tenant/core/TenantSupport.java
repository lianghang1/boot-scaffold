package com.sfi.scaffold.starter.tenant.core;

import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;

/**
 * 租户取值口：三条过滤通路的统一租户来源。
 *
 * <p>唯一来源 = 登录身份（{@link LoginContext}）的 tenantId 一线字段；
 * 无登录态或 tenantId 为 null 即平台视角，全放行不拼条件。</p>
 *
 * @author lianghang
 */
final class TenantSupport {

    private TenantSupport() {
    }

    /** 当前租户 ID；未登录或平台身份返回 null（平台视角） */
    static String currentTenantId() {
        LoginUser user = LoginContext.currentOrNull();
        return user == null ? null : user.getTenantId();
    }
}
