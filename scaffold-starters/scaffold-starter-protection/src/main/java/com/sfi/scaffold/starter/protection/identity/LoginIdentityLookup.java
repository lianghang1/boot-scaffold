package com.sfi.scaffold.starter.protection.identity;

import com.sfi.scaffold.starter.security.identity.LoginContext;

/**
 * 登录身份查找：本类是 protection 内唯一引用 security identity 的隔离点——
 * security 不在 classpath 时本类整体不装配，{@link RequestIdentity} 持有 null 查找器，
 * 其余代码零类引用，保证类加载安全。
 *
 * @author lianghang
 */
public class LoginIdentityLookup {

    /** 当前登录用户 accountId；未登录返回 null。 */
    public Long currentAccountId() {
        com.sfi.scaffold.starter.security.identity.LoginUser user = LoginContext.currentOrNull();
        return user == null ? null : user.getAccountId();
    }
}
