package com.sfi.scaffold.starter.security.permission;

import java.util.List;

/**
 * URL 权限规则来源：由业务侧实现，把「URL 模式 → 权限点」映射交给框架。
 *
 * <p>框架只定义取数契约，不执着数据源——业务从菜单表加载即改库生效、不发版；
 * 没有实现 Bean 时框架退化为「只认证不授权」（任意已登录用户可访问），保持无菜单
 * 体系项目的可用性。</p>
 *
 * @author lianghang
 */
public interface UrlPermissionProvider {

    /** 返回全部 URL 权限规则；空集合表示不做授权收敛。 */
    List<UrlPermissionRule> loadRules();
}
