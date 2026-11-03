package com.sfi.scaffold.starter.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 场景①：只引 security，无任何放行配置——启动正常，所有请求 401 JSON（fail-closed）。
 *
 * @author lianghang
 */
@SpringBootTest(classes = SecurityTestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityDefaultAllLockedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allRequestsUnauthorizedWithJsonBody() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
