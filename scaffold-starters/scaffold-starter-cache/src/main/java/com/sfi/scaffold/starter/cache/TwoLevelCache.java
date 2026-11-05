package com.sfi.scaffold.starter.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * 两级缓存实现，L1 为进程内 Caffeine，L2 为跨实例共享的 Redis。
 *
 * <p>读路径按 L1 → L2 → loader 逐层回源，L2 命中后回填 L1；L2 JSON 带 {@code @class}
 * 类型标识（见 {@code TwoLevelCacheManager}），命中恢复业务原类型。写路径直写 L2 并失效
 * 本实例 L1，其他实例依赖 TTL 收敛，因此并非强一致——适合字典、配置类读多写少
 * 的数据，强一致场景请直接使用 Redis 或在业务层做双删。</p>
 *
 * <p>序列化失败按未命中处理，不向业务抛异常，保证缓存故障不阻断主流程。</p>
 *
 * @author lianghang
 */
public class TwoLevelCache extends AbstractValueAdaptingCache {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(TwoLevelCache.class);

    private final String name;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> l1;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final Duration l2Ttl;

    TwoLevelCache(String name, com.github.benmanes.caffeine.cache.Cache<String, Object> l1,
                  StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                  String keyPrefix, Duration l2Ttl, boolean allowNull) {
        super(allowNull);
        this.name = name;
        this.l1 = l1;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.l2Ttl = l2Ttl;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    protected Object lookup(Object key) {
        String k = key(key);
        Object hit = l1.getIfPresent(k);
        if (hit != null) {
            return hit;
        }
        String json = redisTemplate.opsForValue().get(k);
        if (json == null) {
            return null;
        }
        try {
            Object value = objectMapper.readValue(json, Object.class);
            l1.put(k, value);
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object cached = lookup(key);
        if (cached != null) {
            return (T) fromStoreValue(cached);
        }
        try {
            T value = valueLoader.call();
            put(key, value);
            return value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(Object key, Object value) {
        String k = key(key);
        if (value == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(k, objectMapper.writeValueAsString(value), l2Ttl);
            l1.put(k, value);
        } catch (Exception e) {
            // 序列化失败按未命中处理，不影响业务主流程
        }
    }

    @Override
    public void evict(Object key) {
        String k = key(key);
        redisTemplate.delete(k);
        l1.invalidate(k);
    }

    /**
     * 全清本缓存名：SCAN 删除本前缀全部 L2 key，并精准失效 L1 中本缓存名的条目。
     *
     * <p>SCAN 游标分批不阻塞 Redis 主线程；Redis 故障时仅告警，L1 失效照常完成，
     * 与「缓存故障不阻断主流程」语义一致。</p>
     */
    @Override
    public void clear() {
        String pattern = keyPrefix + name + ":*";
        java.util.Set<String> keys = new java.util.HashSet<>();
        try {
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                try (org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.scan(
                        org.springframework.data.redis.core.ScanOptions.scanOptions()
                                .match(pattern).count(200).build())) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
                return null;
            });
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[cache] 缓存 {} 清理 L2 失败（L1 仍会失效）: {}", name, e.getMessage());
        }
        String ownPrefix = keyPrefix + name + ":";
        l1.asMap().keySet().removeIf(k -> k.startsWith(ownPrefix));
    }

    private String key(Object key) {
        return keyPrefix + name + ":" + key;
    }
}
