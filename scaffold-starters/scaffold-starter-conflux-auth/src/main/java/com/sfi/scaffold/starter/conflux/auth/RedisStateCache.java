package com.sfi.scaffold.starter.conflux.auth;

import com.sfi.scaffold.common.util.JsonUtils;
import com.sfi.conflux.auth.support.StateCache;
import com.sfi.scaffold.starter.redis.RedisOps;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * Redis 版登录 state 缓存：conflux {@link StateCache} SPI 的分布式实现。
 *
 * <p>conflux 默认 {@code InMemoryStateCache} 仅单机可用；本实现借框架已有的
 * {@link RedisOps} 把 state 落到 Redis，集群多实例共享。{@code loadAndRemove}
 * 用 {@code getAndDelete} 原子指令取出即删，天然防重放（避免 get+delete 两步之间的并发重放窗口）。</p>
 *
 * <p>只存 state 与业务透传的 extra，不存 configId/channelCode——与 conflux 契约一致：
 * 回调时 configId/channelCode 由使用方显式传入，不靠 state 定位。</p>
 *
 * @author lianghang
 */
public class RedisStateCache implements StateCache {

    /** state key 前缀，与其他 Redis 数据隔离 */
    private static final String KEY_PREFIX = "conflux:auth:state:";

    private final RedisOps redisOps;

    public RedisStateCache(RedisOps redisOps) {
        this.redisOps = redisOps;
    }

    @Override
    public void save(String state, Map<String, String> extra, int expireSeconds) {
        if (state == null) {
            return;
        }
        Map<String, String> snapshot = extra == null
                ? Collections.<String, String>emptyMap()
                : extra;
        redisOps.set(key(state), snapshot, Duration.ofSeconds(Math.max(1, expireSeconds)));
    }

    @Override
    public Map<String, String> loadAndRemove(String state) {
        if (state == null) {
            return null;
        }
        // 原子取出即删：spring-data-redis 2.6+ 的 ValueOperations.getAndDelete
        String json = redisOps.template().opsForValue().getAndDelete(key(state));
        if (json == null) {
            return null;
        }
        return JsonUtils.fromJson(json, Map.class);
    }

    private static String key(String state) {
        return KEY_PREFIX + state;
    }
}
