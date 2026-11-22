package com.sfi.scaffold.starter.security.permission;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.starter.security.anonymous.Anonymous;
import com.sfi.scaffold.starter.security.identity.AccountType;
import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.NestedServletException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link UrlPermissionInterceptor}：URL + 方法匹配、any-of、最长匹配优先、
 * 幂等命名 {@code @Anonymous} 短路、无命中放行。
 *
 * @author lianghang
 */
class UrlPermissionInterceptorTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        UrlPermissionProvider provider = () -> Arrays.asList(
                new UrlPermissionRule("GET", "/api/order/**", "order:list"),
                new UrlPermissionRule("GET", "/api/order/detail/**", "order:detail"),
                new UrlPermissionRule("POST", "/api/order/**", "order:save", "order:admin"),
                new UrlPermissionRule("*", "/api/report/*", "report:view"));
        UrlPermissionInterceptor interceptor =
                new UrlPermissionInterceptor(provider, new PermissionChecker());
        mvc = MockMvcBuilders.standaloneSetup(new DemoController())
                .addInterceptors(interceptor)
                .build();
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    private void login(String... permissions) {
        LoginUser user = new LoginUser();
        user.setAccountId(1L);
        user.setAccountNo("u");
        user.setAccountType(AccountType.OPERATOR);
        user.setPermissions(new HashSet<>(Arrays.asList(permissions)));
        LoginContext.set(user);
    }

    @RestController
    static class DemoController {
        @GetMapping("/api/order/list") public String list() { return "ok"; }
        @GetMapping("/api/order/detail/1") public String detail() { return "ok"; }
        @PostMapping("/api/order/save") public String save() { return "ok"; }
        @GetMapping("/api/report/daily") public String report() { return "ok"; }
        @Anonymous
        @GetMapping("/api/open/ping") public String open() { return "ok"; }
        @GetMapping("/api/unmapped") public String unmapped() { return "ok"; }
        @GetMapping("/api/public/health") public String publicHealth() { return "ok"; }
    }

    @Test
    void urlMatchedWithPermission() throws Exception {
        login("order:list");
        mvc.perform(get("/api/order/list")).andExpect(status().isOk());
    }

    @Test
    void urlMatchedWithoutPermissionForbidden() {
        login("order:list");
        NestedServletException ex = catchThrowableOfType(
                () -> mvc.perform(post("/api/order/save")), NestedServletException.class);
        assertThat(ex.getRootCause()).isInstanceOf(BizException.class);
        assertThat(((BizException) ex.getRootCause()).getErrorCode().getCode()).isEqualTo(1003);
    }

    @Test
    void anyOfPermissionPasses() throws Exception {
        login("order:admin");
        mvc.perform(post("/api/order/save")).andExpect(status().isOk());
    }

    @Test
    void mostSpecificPatternWins() {
        login("order:list"); // 有宽的 order:list，缺具体的 order:detail
        NestedServletException ex = catchThrowableOfType(
                () -> mvc.perform(get("/api/order/detail/1")), NestedServletException.class);
        assertThat(ex.getRootCause()).isInstanceOf(BizException.class); // 按 detail 规则裁决
    }

    @Test
    void methodSpecificRuleApplies() throws Exception {
        login("report:view");
        mvc.perform(get("/api/report/daily")).andExpect(status().isOk());
    }

    @Test
    void unmappedUrlPasses() throws Exception {
        login();
        mvc.perform(get("/api/unmapped")).andExpect(status().isOk());
    }

    @Test
    void anonymousShortCircuits() throws Exception {
        // 未登录，@Anonymous 方法应跳过 URL 权限（认证边界由 Security 链放行）
        mvc.perform(get("/api/open/ping")).andExpect(status().isOk());
    }

    @Test
    void noProviderMeansNoAuthorization() throws Exception {
        UrlPermissionInterceptor noop = new UrlPermissionInterceptor(null, new PermissionChecker());
        MockMvc m = MockMvcBuilders.standaloneSetup(new DemoController())
                .addInterceptors(noop).build();
        login();
        m.perform(get("/api/order/list")).andExpect(status().isOk());
    }

    // ===== 严格模式：未命中规则拒绝，白名单/匿名仍放行 =====

    private MockMvc strictMvc() {
        UrlPermissionProvider provider = () -> Arrays.asList(
                new UrlPermissionRule("GET", "/api/order/**", "order:list"));
        UrlPermissionInterceptor strict = new UrlPermissionInterceptor(
                provider, new PermissionChecker(), true, Arrays.asList("/api/public/**"));
        return MockMvcBuilders.standaloneSetup(new DemoController())
                .addInterceptors(strict).build();
    }

    @Test
    void strictModeRejectsUnmappedUrl() {
        MockMvc m = strictMvc();
        login("order:list");
        NestedServletException ex = catchThrowableOfType(
                () -> m.perform(get("/api/unmapped")), NestedServletException.class);
        assertThat(ex.getRootCause()).isInstanceOf(BizException.class);
        assertThat(((BizException) ex.getRootCause()).getErrorCode().getCode()).isEqualTo(1003);
    }

    @Test
    void strictModeMatchedUrlStillAuthorized() throws Exception {
        MockMvc m = strictMvc();
        login("order:list");
        m.perform(get("/api/order/list")).andExpect(status().isOk());
    }

    @Test
    void strictModePermitUrlShortCircuits() throws Exception {
        MockMvc m = strictMvc();
        login(); // 无任何权限
        m.perform(get("/api/public/health")).andExpect(status().isOk());
    }

    @Test
    void strictModeAnonymousShortCircuits() throws Exception {
        MockMvc m = strictMvc();
        // 未登录、未命中规则，@Anonymous 仍放行
        m.perform(get("/api/open/ping")).andExpect(status().isOk());
    }

    @Test
    void strictModeNoProviderRejectsUnmapped() {
        UrlPermissionInterceptor strict = new UrlPermissionInterceptor(
                null, new PermissionChecker(), true, null);
        MockMvc m = MockMvcBuilders.standaloneSetup(new DemoController())
                .addInterceptors(strict).build();
        login();
        NestedServletException ex = catchThrowableOfType(
                () -> m.perform(get("/api/order/list")), NestedServletException.class);
        assertThat(ex.getRootCause()).isInstanceOf(BizException.class);
    }
}
