package com.sfi.scaffold.starter.security.permission;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.starter.security.identity.AccountType;
import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 编程式权限裁决测试：与注解路径同语义（任一命中、通配、超管全量），未登录 has=false、require=401。
 *
 * @author lianghang
 */
class PermissionCheckerTest {

    private final PermissionChecker checker = new PermissionChecker();

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    @Test
    void hasShouldBeFalseWhenAnonymous() {
        assertThat(checker.has("system:account:manage")).isFalse();
    }

    @Test
    void requireShouldThrow401WhenAnonymous() {
        BizException e = catchThrowableOfType(() -> checker.require("system:account:manage"), BizException.class);
        assertThat(e.getErrorCode().getCode()).isEqualTo(1001);
    }

    @Test
    void hasShouldMatchAnyOfValues() {
        login("system:audit:query");
        assertThat(checker.has("system:account:manage", "system:audit:query")).isTrue();
        assertThat(checker.has("system:account:manage")).isFalse();
    }

    @Test
    void wildcardShouldMatch() {
        login("system:account:*");
        assertThat(checker.has("system:account:manage")).isTrue();
    }

    @Test
    void requireShouldPassWithPermissionAndThrow403Without() {
        login("system:dict:manage");
        assertThatCode(() -> checker.require("system:account:manage", "system:dict:manage"))
                .doesNotThrowAnyException();

        BizException e = catchThrowableOfType(() -> checker.require("system:account:manage"), BizException.class);
        assertThat(e.getErrorCode().getCode()).isEqualTo(1003);
        assertThat(e).hasMessageContaining("system:account:manage");
    }

    private void login(String... permissions) {
        LoginUser user = new LoginUser();
        user.setAccountId(1L);
        user.setAccountNo("admin");
        user.setAccountType(AccountType.OPERATOR);
        user.setDisplayName("管理员");
        user.setPermissions(new java.util.HashSet<>(java.util.Arrays.asList(permissions)));
        LoginContext.set(user);
    }
}
