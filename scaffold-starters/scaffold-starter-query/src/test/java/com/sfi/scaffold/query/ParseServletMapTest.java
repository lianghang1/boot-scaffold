package com.sfi.scaffold.query;

import com.sfi.scaffold.query.filter.OperatorX;
import com.sfi.scaffold.query.filter.QueryFilterX;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link QueryUtilsX#parse(Map)} 可直接吃 servlet 的
 * {@code request.getParameterMap()}（value 为 String[]）。
 */
class ParseServletMapTest {

    private static Map<String, String[]> servletParams() {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("EQ_status", new String[]{"1"});              // 单值
        params.put("CN_name", new String[]{"张", "李"});        // 多值：取首个
        params.put("GE_age", new String[]{" 20 "});              // 单值带空格：trim
        params.put("NU_deletedAt", new String[]{""});            // 无值运算符
        params.put("NOT_A_FILTER", new String[]{"x"});           // 非法前缀：跳过
        params.put("page", new String[]{"0"});                   // 非运算符：跳过
        params.put("emptyValue", new String[]{});                 // 空数组：跳过
        return params;
    }

    @Test
    void parseAcceptsServletParameterMap() {
        List<QueryFilterX> filters = QueryUtilsX.parse(servletParams());

        assertEquals(4, filters.size(), "EQ/CN/GE/NU 四条条件，其余跳过");

        QueryFilterX eq = filters.get(0);
        assertEquals("status", eq.getFieldName());
        assertEquals(OperatorX.EQ, eq.getOperator());
        assertEquals("1", eq.getValue());

        QueryFilterX cn = filters.get(1);
        assertEquals("name", cn.getFieldName());
        assertEquals(OperatorX.CN, cn.getOperator());
        assertEquals("张", cn.getValue(), "多值取首个，不是 [Ljava.lang.String;@xxx");

        QueryFilterX ge = filters.get(2);
        assertEquals("20", ge.getValue(), "value 应 trim");

        QueryFilterX nu = filters.get(3);
        assertEquals(OperatorX.NU, nu.getOperator());
        assertTrue(nu.getValue() == null, "无值运算符 value 为 null");
    }

    @Test
    void parseAcceptsPlainSingleValueMap() {
        Map<String, Object> plain = new LinkedHashMap<>();
        plain.put("EQ_status", 1);
        plain.put("CN_name", "张");
        List<QueryFilterX> filters = QueryUtilsX.parse(plain);
        assertEquals(2, filters.size());
        assertEquals("1", filters.get(0).getValue());
    }
}
