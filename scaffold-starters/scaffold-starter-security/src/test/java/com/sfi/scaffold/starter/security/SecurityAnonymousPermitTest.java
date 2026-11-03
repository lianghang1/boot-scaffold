package com.sfi.scaffold.starter.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 场景②：@Anonymous 注解放行（方法级 + 类级 + 未声明 method 的全方法注册），其余仍 401。
 *
 * @author lianghang
 */
@SpringBootTest(classes = SecurityTestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityAnonymousPermitTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void annotatedEndpointPermittedAllMethods() throws Exception {
        mockMvc.perform(get("/open/ping")).andExpect(status().isOk());
        mockMvc.perform(post("/open/any")).andExpect(status().isOk());
    }

    @Test
    void nonAnnotatedEndpointStillLocked() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001));
    }
}
