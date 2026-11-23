package com.sfi.scaffold.starter.conflux.auth.config;

import com.sfi.conflux.auth.support.StateCache;
import com.sfi.scaffold.starter.conflux.auth.RedisStateCache;
import com.sfi.scaffold.starter.redis.RedisOps;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Conflux 登录能力桥接装配：给 conflux 的 {@code AuthGateway} 补一个分布式 state 缓存。
 *
 * <p>conflux 的 starter 负责装配 {@code AuthGateway} 与默认 {@code AuthConfigProvider}；
 * 本配置只做 framework 层该补的一件事——把 state 缓存从单机内存换成 Redis（框架已有能力）。
 * 用 {@code @AutoConfigureBefore} 保证本 Bean 先于 conflux 的 AuthAutoConfiguration 就绪，
 * 后者通过 {@code ObjectProvider<StateCache>} 收集到它并注入网关。</p>
 *
 * <p>业务侧如需自定义 state 存储，提供自己的 {@link StateCache} Bean 即可覆盖默认实现；
 * 如需把登录配置接到 DB/配置中心，提供自己的 {@code AuthConfigProvider} Bean 覆盖 conflux 默认实现。</p>
 *
 * @author lianghang
 */
@AutoConfiguration
@AutoConfigureBefore(name = "com.sfi.conflux.auth.autoconfigure.AuthAutoConfiguration")
@ConditionalOnClass(StateCache.class)
public class ConfluxAuthBridgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StateCache.class)
    @ConditionalOnBean(RedisOps.class)
    public StateCache redisStateCache(RedisOps redisOps) {
        return new RedisStateCache(redisOps);
    }
}
