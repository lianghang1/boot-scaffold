package com.sfi.scaffold.common;

import com.sfi.scaffold.common.util.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StringUtils 门面测试：lang3 静态穿透 + 自有方法委托 guava。
 *
 * @author lianghang
 */
class StringUtilsTest {

    @Test
    void lang3MethodsFlowThroughSubclass() {
        // 静态方法经子类穿透：继承即得全量 lang3 能力
        assertTrue(StringUtils.isBlank("  "));
        assertFalse(StringUtils.isBlank("x"));
        assertTrue(StringUtils.isEmpty(""));
        assertEquals("abc", StringUtils.defaultIfEmpty("", "abc"));
        assertEquals("ab...", StringUtils.abbreviate("abcdef", 5));
        assertNull(StringUtils.trimToNull("   "));
    }

    @Test
    void camelSnakeConvert() {
        assertEquals("user_name", StringUtils.camelToSnake("userName"));
        assertEquals("user_name", StringUtils.camelToSnake("UserName"));
        assertEquals("userName", StringUtils.snakeToCamel("user_name"));
        assertNull(StringUtils.camelToSnake(null));
        assertNull(StringUtils.snakeToCamel(null));
        // 往返一致
        assertEquals("orderItemId", StringUtils.snakeToCamel(StringUtils.camelToSnake("orderItemId")));
    }

    @Test
    void formatPlaceholder() {
        assertEquals("用户张三不存在: 42", StringUtils.format("用户{}不存在: {}", "张三", 42));
        assertEquals("a-x-b", StringUtils.format("a-{}-b", "x", "y"));
        assertEquals("a-xb", StringUtils.format("a-{}b", "x"));
        assertEquals("no placeholder", StringUtils.format("no placeholder", "x"));
        assertEquals("plain", StringUtils.format("plain"));
        assertNull(StringUtils.format(null, "x"));
        assertEquals("null 值", StringUtils.format("{} 值", (Object) null));
        assertEquals("花括号{}保留", StringUtils.format("花括号{}保留"));
    }
}
