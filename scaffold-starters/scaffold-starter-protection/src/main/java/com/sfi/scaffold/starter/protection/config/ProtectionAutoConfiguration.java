package com.sfi.scaffold.starter.protection.config;

import com.sfi.scaffold.starter.protection.abuse.AbuseGuard;
import com.sfi.scaffold.starter.protection.idempotent.IdempotentAspect;
import com.sfi.scaffold.starter.protection.lock.DistributedLockAspect;
import com.sfi.scaffold.starter.protection.lock.RedisLockUtil;
import com.sfi.scaffold.starter.protection.ratelimit.RateLimitAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 服务保护能力的自动装配类。
 *
 * <p>集中装配限流、幂等、分布式锁三个切面及其依赖的 RedisLockUtil——
 * 引入即生效，业务侧只需打注解，无需手动注册 Bean。</p>
 *
 * <p>类路径无 Redis（如 server-lite 单机零依赖运行）时本装配整体跳过，
 * 各切面 Bean 不注册——限流/幂等/锁注解静默不生效，业务侧无需改动。</p>
 *
 * @author lianghang
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
public class ProtectionAutoConfiguration {

    @Bean
    public RedisLockUtil redisLockUtil(StringRedisTemplate redisTemplate) {
        return new RedisLockUtil(redisTemplate);
    }

    @Bean
    public AbuseGuard abuseGuard(StringRedisTemplate redisTemplate, com.sfi.scaffold.common.metrics.CounterMetrics counterMetrics) {
        return new AbuseGuard(redisTemplate, counterMetrics);
    }

    @Bean
    public RateLimitAspect rateLimitAspect(StringRedisTemplate redisTemplate) {
        return new RateLimitAspect(redisTemplate);
    }

    @Bean
    public IdempotentAspect idempotentAspect(StringRedisTemplate redisTemplate) {
        return new IdempotentAspect(redisTemplate);
    }

    @Bean
    public DistributedLockAspect distributedLockAspect(RedisLockUtil lockUtil) {
        return new DistributedLockAspect(lockUtil);
    }

    /** micrometer 在场时绑定真实打点；嵌套类经 ASM 元数据判定条件，类加载安全 */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MicrometerCounterMetricsConfiguration {

        @org.springframework.context.annotation.Bean
        com.sfi.scaffold.common.metrics.CounterMetrics micrometerCounterMetrics(
                org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> registry) {
            return new com.sfi.scaffold.common.metrics.MicrometerCounterMetrics(registry.getIfAvailable());
        }
    }

    /** 兜底空实现：micrometer 不在场时静默 */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(com.sfi.scaffold.common.metrics.CounterMetrics.class)
    static class DefaultCounterMetricsConfiguration {

        @org.springframework.context.annotation.Bean
        com.sfi.scaffold.common.metrics.CounterMetrics noopCounterMetrics() {
            return com.sfi.scaffold.common.metrics.CounterMetrics.NOOP;
        }
    }
}
