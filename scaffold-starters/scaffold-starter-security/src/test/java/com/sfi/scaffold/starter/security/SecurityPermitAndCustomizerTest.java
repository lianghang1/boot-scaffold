package com.sfi.scaffold.starter.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.servlet.MockMvc;

import com.sfi.scaffold.starter.data.jpa.config.DataJpaAutoConfiguration;
import com.sfi.scaffold.starter.security.security.ScaffoldSecurityCustomizer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 场景③④：yml permit-urls 白名单放行 + 项目 Customizer 追加规则放行。
 *
 * @author lianghang
 */
@SpringBootTest(classes = SecurityPermitAndCustomizerTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "scaffold.security.permit-urls=/public/**")
@AutoConfigureMockMvc
class SecurityPermitAndCustomizerTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, DataJpaAutoConfiguration.class})
    @Import({SecurityTestApp.PingController.class, SecurityTestApp.OpenController.class})
    static class App {
        @Bean
        ScaffoldSecurityCustomizer projectRule() {
            return new ScaffoldSecurityCustomizer() {
                @Override
                public void customize(HttpSecurity http) throws Exception {
                    http.authorizeRequests().antMatchers("/custom/**").permitAll();
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void permitUrlsWhitelisted() throws Exception {
        mockMvc.perform(get("/public/data")).andExpect(status().isNotFound());
    }

    @Test
    void customizerRuleApplied() throws Exception {
        mockMvc.perform(get("/custom/data")).andExpect(status().isNotFound());
    }

    @Test
    void unlistedPathStillLocked() throws Exception {
        mockMvc.perform(get("/ping")).andExpect(status().isUnauthorized());
    }
}
