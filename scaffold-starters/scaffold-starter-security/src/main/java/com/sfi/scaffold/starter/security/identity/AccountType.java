package com.sfi.scaffold.starter.security.identity;

/**
 * 系统统一账户类型的枚举。
 *
 * <p>不同入口的认证方式各异，但认证通过后都归一成一种账户身份；
 * 下游鉴权按本类型区分角色与权限的加载方式。</p>
 *
 * @author lianghang
 */
public enum AccountType {

    /** C 端用户（社交授权后创建） */
    MEMBER,

    /** 内部操作员（账密登录，挂 RBAC 角色） */
    OPERATOR,

    /** 应用/服务账号（凭据换 token，挂 scope） */
    APP
}
