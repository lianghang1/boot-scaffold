package com.sfi.scaffold.starter.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 缓存自动装配器，提供默认的两级缓存实现。
 *
 * <p>Redis 在场时装配 Caffeine + Redis 的 {@code TwoLevelCacheManager}；
 * Redis 缺席时退化为纯 Caffeine 本地缓存（单机语义，L2 静默缺席），
 * {@code @Cacheable} 用法不变。两者仅在容器中尚无 {@code CacheManager} 时生效，
 * 业务方可自行注册实现覆盖；配置前缀为 {@code scaffold.cache}，
 * 同时启用声明式缓存注解支持。</p>
 *
 * <p>当 {@code scaffold.cache.type=redis} 时不注册任何 Bean，退化为 Boot
 * 自动配置提供的纯 Redis 缓存——两级缓存适合字典、配置类读多写少的数据，
 * 强一致场景请勿依赖本装配器。</p>
 *
 * @author lianghang
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CachePropertiesHolder.class)
public class CacheAutoConfiguration {

    /**
     * Redis 在场：Caffeine L1 + Redis L2 两级缓存。
     * 嵌套类经 ASM 元数据判定条件，Redis 不在类路径时本类不加载，类引用安全。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
    static class RedisBackedConfiguration {

        @Bean
        @ConditionalOnMissingBean(CacheManager.class)
        public CacheManager twoLevelCacheManager(StringRedisTemplate redisTemplate,
                                                 ObjectMapper objectMapper,
                                                 CachePropertiesHolder properties) {
            return new TwoLevelCacheManager(
                    Caffeine.newBuilder()
                            .maximumSize(properties.getL1MaxSize())
                            .expireAfterWrite(Duration.ofSeconds(properties.getL1TtlSeconds()))
                            .build(),
                    redisTemplate, objectMapper,
                    properties.getKeyPrefix(),
                    Duration.ofSeconds(properties.getL2TtlSeconds()));
        }
    }

    /** 无 Redis 退化：纯 Caffeine 本地缓存（单机语义），L1 TTL/容量沿用 scaffold.cache 配置 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("org.springframework.data.redis.core.StringRedisTemplate")
    static class NoRedisDegradedConfiguration {

        @Bean
        @ConditionalOnMissingBean(CacheManager.class)
        public CacheManager caffeineCacheManager(CachePropertiesHolder properties) {
            CaffeineCacheManager cacheManager = new CaffeineCacheManager();
            cacheManager.setCaffeine(Caffeine.newBuilder()
                    .maximumSize(properties.getL1MaxSize())
                    .expireAfterWrite(Duration.ofSeconds(properties.getL1TtlSeconds())));
            cacheManager.setCacheNames(java.util.Collections.emptySet());
            return cacheManager;
        }
    }
}
