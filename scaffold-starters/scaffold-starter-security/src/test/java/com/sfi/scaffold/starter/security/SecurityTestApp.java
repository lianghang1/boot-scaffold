package com.sfi.scaffold.starter.security;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import com.sfi.scaffold.starter.data.jpa.config.DataJpaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sfi.scaffold.starter.security.anonymous.Anonymous;

import java.util.Collections;
import java.util.Map;

@Configuration
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
                    com.sfi.scaffold.starter.data.jpa.config.DataJpaAutoConfiguration.class})
@Import({SecurityTestApp.PingController.class, SecurityTestApp.OpenController.class})
public class SecurityTestApp {

    @RestController
    static class PingController {

        @GetMapping("/ping")
        public Map<String, String> ping() {
            return Collections.singletonMap("ok", "1");
        }
    }

    @RestController
    @Anonymous
    static class OpenController {

        @GetMapping("/open/ping")
        public Map<String, String> ping() {
            return Collections.singletonMap("ok", "1");
        }

        @RequestMapping("/open/any")
        public Map<String, String> any() {
            return Collections.singletonMap("ok", "1");
        }
    }
}
