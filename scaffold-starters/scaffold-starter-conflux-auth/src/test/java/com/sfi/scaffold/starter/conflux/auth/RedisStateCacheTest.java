package com.sfi.scaffold.starter.conflux.auth;

import com.sfi.scaffold.common.util.JsonUtils;
import com.sfi.scaffold.starter.redis.RedisOps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisStateCache 单测：save 落 Redis + TTL、loadAndRemove 原子取出即删、未命中返回 null。
 * 不连真实 Redis，mock StringRedisTemplate 的原子 getAndDelete。
 *
 * @author lianghang
 */
class RedisStateCacheTest {

    private StringRedisTemplate template;
    private ValueOperations<String, String> valueOps;
    private RedisOps redisOps;
    private RedisStateCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        template = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOps);
        redisOps = mock(RedisOps.class);
        when(redisOps.template()).thenReturn(template);
        cache = new RedisStateCache(redisOps);
    }

    @Test
    void save_writesJsonWithTtl() {
        Map<String, String> extra = new LinkedHashMap<String, String>();
        extra.put("biz", "ctx");

        cache.save("state-1", extra, 300);

        verify(redisOps).set(eq("conflux:auth:state:state-1"), eq(extra), eq(Duration.ofSeconds(300)));
    }

    @Test
    void save_nullState_isNoop() {
        cache.save(null, null, 300);
        verify(redisOps, org.mockito.Mockito.never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void loadAndRemove_hits_returnsExtra() {
        Map<String, String> extra = new LinkedHashMap<String, String>();
        extra.put("biz", "ctx");
        when(valueOps.getAndDelete("conflux:auth:state:state-1")).thenReturn(JsonUtils.toJson(extra));

        Map<String, String> got = cache.loadAndRemove("state-1");

        assertNotNull(got);
        assertEquals("ctx", got.get("biz"));
    }

    @Test
    void loadAndRemove_miss_returnsNull() {
        when(valueOps.getAndDelete("conflux:auth:state:missing")).thenReturn(null);
        assertNull(cache.loadAndRemove("missing"));
    }

    @Test
    void loadAndRemove_nullState_returnsNull() {
        assertNull(cache.loadAndRemove(null));
    }
}
