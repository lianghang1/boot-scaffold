package com.sfi.scaffold.starter.tenant.bs;

import com.sfi.scaffold.starter.tenant.annotation.TenantIgnore;
import com.sfi.scaffold.starter.tenant.bs.TenantParamFilter;
import com.sfi.scaffold.query.plan.annotation.ColumnX;
import com.sfi.scaffold.query.plan.annotation.TableX;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenantParamFilter 注入核心单测（与 BS API 解耦的 inject 方法）：
 * 字段探测注入、外部参数覆盖、豁免通道、豁免名单、非租户计划放行。
 *
 * @author lianghang
 */
class TenantParamFilterTest {

    private static final Iterable<String> NO_IGNORE = Collections.emptyList();

    @Test
    void planWithTenantFieldShouldBeInjected() {
        Map<String, Object> result = TenantParamFilter.inject(
                OrderPlan.class, fieldSet(OrderPlan.class), new HashMap<>(), "t1", false, NO_IGNORE);
        assertThat(result).containsEntry(TenantParamFilter.TENANT_FIELD, "t1");
    }

    @Test
    void externalTenantParamShouldBeOverwritten() {
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", "t9");
        params.put("tenantId_op", "ne");
        params.put("name", "abc");

        Map<String, Object> result = TenantParamFilter.inject(
                OrderPlan.class, fieldSet(OrderPlan.class), params, "t1", false, NO_IGNORE);

        assertThat(result).containsEntry(TenantParamFilter.TENANT_FIELD, "t1");
        assertThat(result).doesNotContainKey("tenantId_op");
        // 无关动态参数保留
        assertThat(result).containsEntry("name", "abc");
    }

    @Test
    void ignoredPlanClassShouldPassThrough() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> result = TenantParamFilter.inject(
                IgnoredPlan.class, fieldSet(IgnoredPlan.class), params, "t1", false, NO_IGNORE);
        assertThat(result).isSameAs(params).doesNotContainKey(TenantParamFilter.TENANT_FIELD);
    }

    @Test
    void methodLevelIgnoreMarkShouldPassThrough() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> result = TenantParamFilter.inject(
                OrderPlan.class, fieldSet(OrderPlan.class), params, "t1", true, NO_IGNORE);
        assertThat(result).isSameAs(params).doesNotContainKey(TenantParamFilter.TENANT_FIELD);
    }

    @Test
    void ignoredTableShouldPassThrough() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> result = TenantParamFilter.inject(
                OrderPlan.class, fieldSet(OrderPlan.class), params, "t1", false,
                Collections.singletonList("t_order"));
        assertThat(result).isSameAs(params).doesNotContainKey(TenantParamFilter.TENANT_FIELD);
    }

    @Test
    void planWithoutTenantFieldShouldPassThrough() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> result = TenantParamFilter.inject(
                PlainPlan.class, fieldSet(PlainPlan.class), params, "t1", false, NO_IGNORE);
        assertThat(result).isSameAs(params);
    }

    private static Set<String> fieldSet(Class<?> clazz) {
        Set<String> fields = new HashSet<>();
        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
            fields.add(f.getName());
        }
        return fields;
    }

    @TableX(name = "t_order", alias = "o")
    static class OrderPlan {
        @ColumnX("o.id")
        private Long id;
        @ColumnX("o.tenant_id")
        private String tenantId;
    }

    @TenantIgnore
    @TableX(name = "t_order", alias = "o")
    static class IgnoredPlan {
        @ColumnX("o.tenant_id")
        private String tenantId;
    }

    @TableX(name = "t_config", alias = "c")
    static class PlainPlan {
        @ColumnX("c.id")
        private Long id;
    }
}
