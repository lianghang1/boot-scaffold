package com.sfi.scaffold.starter.tenant.core;

import com.sfi.scaffold.starter.security.identity.AccountType;
import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;

import java.util.Collections;
import java.util.function.Supplier;

/**
 * 模拟租户执行器：定时任务、MQ 消费等无 HTTP 登录态的场景，在指定租户视角下执行一段逻辑。
 *
 * <p>底座即 {@link LoginContext}（租户上下文 = 登录身份的一线字段，不另立 TTL）：
 * <ul>
 *   <li>当前线程已有登录身份：拷贝身份并把 tenantId 换成目标租户，执行完<b>恢复原身份</b>；</li>
 *   <li>无登录身份：写入一个 accountId=0 的合成身份（对齐 {@code LoginUser.system} 先例），
 *       执行完 {@link LoginContext#clear()} 清场。</li>
 * </ul></p>
 *
 * <p>TTL 底座保证块内提交到 @Async / ScaffoldExecutors 线程池的任务继承租户视角。
 * <b>注意</b>：{@code LoginContext.set} 会同步审计人（createBy/updateBy）——无登录态的
 * 合成身份审计人落 {@code 0}，有登录态则保持原 accountId 不变。</p>
 *
 * <p>必须用本方法而非裸 {@code LoginContext.set}：try/finally 恢复现场，防止忘记
 * clear 导致租户视角沿线程池泄漏串号。</p>
 *
 * @author lianghang
 */
public final class TenantScope {

    private TenantScope() {
    }

    /** 在指定租户视角下执行（无返回值场景） */
    public static void execute(String tenantId, Runnable action) {
        executeInternal(tenantId, () -> {
            action.run();
            return null;
        });
    }

    /** 在指定租户视角下执行并取回结果 */
    public static <T> T execute(String tenantId, Supplier<T> action) {
        return executeInternal(tenantId, action);
    }

    private static <T> T executeInternal(String tenantId, Supplier<T> action) {
        LoginUser previous = LoginContext.currentOrNull();
        if (previous == null) {
            LoginContext.set(synthetic(tenantId));
            try {
                return action.get();
            } finally {
                LoginContext.clear();
            }
        }
        LoginContext.set(scoped(previous, tenantId));
        try {
            return action.get();
        } finally {
            LoginContext.set(previous);
        }
    }

    /** 拷贝当前身份并替换租户：权限/角色原样保留，其余字段不动 */
    private static LoginUser scoped(LoginUser source, String tenantId) {
        LoginUser scoped = new LoginUser();
        scoped.setAccountId(source.getAccountId());
        scoped.setAccountNo(source.getAccountNo());
        scoped.setAccountType(source.getAccountType());
        scoped.setDisplayName(source.getDisplayName());
        scoped.setRoles(source.getRoles());
        scoped.setPermissions(source.getPermissions());
        scoped.setDevice(source.getDevice());
        scoped.setTenantId(tenantId);
        scoped.setAttributes(source.getAttributes());
        return scoped;
    }

    /** 无登录态的合成身份：不入库的身份，仅承载租户视角（审计人落 0） */
    private static LoginUser synthetic(String tenantId) {
        LoginUser user = new LoginUser();
        user.setAccountId(0L);
        user.setAccountNo("tenant-scope");
        user.setAccountType(AccountType.OPERATOR);
        user.setDisplayName("tenant-scope:" + (tenantId == null ? "platform" : tenantId));
        user.setPermissions(Collections.emptySet());
        user.setDevice("tenant-scope");
        user.setTenantId(tenantId);
        return user;
    }
}
