package com.sfi.scaffold.common.config;

import com.sfi.scaffold.common.util.SpringContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * scaffold-common 自动装配：注册 {@link SpringContextHolder} 静态持有器 + 启动装配自检。
 *
 * <p>EventOps/IdOps/DataCipherHolder 等静态出口都依赖它经容器回填，
 * 引入 scaffold-common 即自动生效，业务侧无需手工注册。</p>
 *
 * @author lianghang
 */
@AutoConfiguration
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SpringContextHolder springContextHolder() {
        return new SpringContextHolder();
    }

    /**
     * 启动就绪报告：打印生效形态的关键装配项。
     *
     * <p>排查「配置没生效」的第一入口。</p>
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> scaffoldStartupReport() {
        return event -> {
            Environment env = event.getApplicationContext().getEnvironment();
            Logger log = LoggerFactory.getLogger("scaffold.startup");
            log.info("[scaffold] 应用就绪 —— app={}, profiles={}",
                    env.getProperty("spring.application.name", "unnamed"),
                    Arrays.toString(env.getActiveProfiles()));
            reportIfPresent(log, env, "scaffold.security.mode", "安全模式");
            reportIfPresent(log, env, "scaffold.crypto.type", "字段加密");
            reportIfPresent(log, env, "scaffold.job.engine", "调度引擎");
            reportIfPresent(log, env, "scaffold.push.enabled", "SSE 推送");
            reportIfPresent(log, env, "scaffold.ws.enabled", "WebSocket");
            reportIfPresent(log, env, "scaffold.data.worker-id", "雪花 worker-id");
            log.info("[scaffold] 配置项总表见 README");
        };
    }

    /** 未配置的项跳过——lite 形态自然只报告它有的能力 */
    private void reportIfPresent(Logger log, Environment env, String key, String label) {
        String value = env.getProperty(key);
        if (value != null && !value.isEmpty()) {
            log.info("[scaffold]   {} = {}", label, value);
        }
    }
}
