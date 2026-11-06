package com.sfi.scaffold.starter.tenant.core;

import com.sfi.scaffold.starter.security.identity.AccountType;
import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenantScope / TenantIgnoreHolder 单测：租户视角切换、现场恢复、合成身份清场、豁免深度计数。
 *
 * @author lianghang
 */
class TenantScopeTest {

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    @Test
    void executeWithoutLoginShouldUseSyntheticIdentityAndClearAfterwards() {
        AtomicReference<String> seen = new AtomicReference<>();
        TenantScope.execute("t1", () -> {
            LoginUser user = LoginContext.current();
            seen.set(user.getTenantId());
            assertThat(user.getAccountId()).isZero();
            assertThat(user.getAccountType()).isEqualTo(AccountType.OPERATOR);
        });
        assertThat(seen.get()).isEqualTo("t1");
        // 执行完清场：不留租户视角沿线程池泄漏
        assertThat(LoginContext.currentOrNull()).isNull();
    }

    @Test
    void executeWithLoginShouldSwapTenantAndRestorePreviousIdentity() {
        LoginUser original = user("t1");
        LoginContext.set(original);

        AtomicReference<String> seen = new AtomicReference<>();
        TenantScope.execute("t2", () -> seen.set(LoginContext.current().getTenantId()));

        assertThat(seen.get()).isEqualTo("t2");
        // 现场恢复：权限、身份、原租户原样
        LoginUser restored = LoginContext.current();
        assertThat(restored.getTenantId()).isEqualTo("t1");
        assertThat(restored.getAccountId()).isEqualTo(original.getAccountId());
        assertThat(restored.getPermissions()).isEqualTo(original.getPermissions());
    }

    @Test
    void executeShouldRestoreIdentityEvenOnException() {
        LoginContext.set(user("t1"));
        try {
            TenantScope.execute("t2", () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // ignore
        }
        assertThat(LoginContext.currentOrNull()).isNotNull();
        assertThat(LoginContext.current().getTenantId()).isEqualTo("t1");
    }

    @Test
    void ignoreHolderShouldCountDepthInsteadOfBoolean() {
        assertThat(TenantIgnoreHolder.isMarked()).isFalse();
        TenantIgnoreHolder.mark();
        TenantIgnoreHolder.mark();
        assertThat(TenantIgnoreHolder.isMarked()).isTrue();
        // 内层豁免方法返回：只减一层，外层豁免仍生效
        TenantIgnoreHolder.clear();
        assertThat(TenantIgnoreHolder.isMarked()).isTrue();
        TenantIgnoreHolder.clear();
        assertThat(TenantIgnoreHolder.isMarked()).isFalse();
        // 多余的 clear 不抛错
        TenantIgnoreHolder.clear();
        assertThat(TenantIgnoreHolder.isMarked()).isFalse();
    }

    private static LoginUser user(String tenantId) {
        LoginUser user = new LoginUser();
        user.setAccountId(1L);
        user.setAccountNo("admin");
        user.setAccountType(AccountType.OPERATOR);
        user.setDisplayName("管理员");
        user.setPermissions(new HashSet<>(java.util.Collections.singletonList("system:demo:manage")));
        user.setTenantId(tenantId);
        return user;
    }
}
