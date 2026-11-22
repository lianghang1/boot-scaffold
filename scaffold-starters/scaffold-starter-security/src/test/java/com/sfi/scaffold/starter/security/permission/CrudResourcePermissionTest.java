package com.sfi.scaffold.starter.security.permission;

import com.sfi.scaffold.common.annotation.CrudResource;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 PermissionInterceptor 从类级 @CrudResource 按方法名归约动作取权限点：
 * list→listPerm、get→回退 listPerm、save→savePerm、delete→回退 savePerm。
 *
 * @author lianghang
 */
class CrudResourcePermissionTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CrudController())
                .addInterceptors(new PermissionInterceptor(new PermissionChecker()))
                .build();
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    private void login(String... permissions) {
        LoginUser user = new LoginUser();
        user.setAccountId(1L);
        user.setAccountNo("admin");
        user.setAccountType(AccountType.OPERATOR);
        user.setPermissions(new HashSet<>(Arrays.asList(permissions)));
        LoginContext.set(user);
    }

    @RestController
    @CrudResource(entity = Object.class, dto = Object.class,
            listPerm = "m:list", savePerm = "m:save")
    static class CrudController {
        @GetMapping("/crud/list")
        public String list() { return "list"; }
        @GetMapping("/crud/get")
        public String get() { return "get"; }
        @PostMapping("/crud/save")
        public String save() { return "save"; }
        @PostMapping("/crud/delete")
        public String delete() { return "delete"; }
    }

    @Test
    void listRequiresListPerm() throws Exception {
        login("m:list");
        mockMvc.perform(get("/crud/list")).andExpect(status().isOk());
    }

    @Test
    void getFallsBackToListPerm() throws Exception {
        login("m:list");
        mockMvc.perform(get("/crud/get")).andExpect(status().isOk());
    }

    @Test
    void saveRequiresSavePerm() throws Exception {
        login("m:save");
        mockMvc.perform(post("/crud/save")).andExpect(status().isOk());
    }

    @Test
    void deleteFallsBackToSavePerm() throws Exception {
        login("m:save");
        mockMvc.perform(post("/crud/delete")).andExpect(status().isOk());
    }

    @Test
    void wrongPermIsForbidden() {
        login("m:list");
        NestedServletException ex = catchThrowableOfType(
                () -> mockMvc.perform(post("/crud/save")), NestedServletException.class);
        assertThat(ex.getRootCause()).isInstanceOf(com.sfi.scaffold.common.exception.BizException.class);
        assertThat(((com.sfi.scaffold.common.exception.BizException) ex.getRootCause())
                .getErrorCode().getCode()).isEqualTo(1003);
    }
}
