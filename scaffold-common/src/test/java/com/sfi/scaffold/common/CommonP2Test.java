package com.sfi.scaffold.common;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.common.request.PageParam;
import com.sfi.scaffold.common.request.SortablePageParam;
import com.sfi.scaffold.common.request.SortingField;
import com.sfi.scaffold.common.util.CollUtils;
import com.sfi.scaffold.common.util.EntityDiff;
import com.sfi.scaffold.common.util.JsonUtils;
import com.sfi.scaffold.common.util.LocalDateTimeUtils;
import com.sfi.scaffold.common.util.SpringExpressionUtils;
import com.sfi.scaffold.common.validation.ValidationUtils;
import org.junit.jupiter.api.Test;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonP2Test {

    // ---------- JsonUtils Long→String ----------

    static class WithId {
        public Long id = 1234567890123456789L;
        public Integer count = 3;
    }

    @Test
    void longSerializedAsString() {
        String json = JsonUtils.toJson(new WithId());
        assertTrue(json.contains("\"id\":\"1234567890123456789\""));
        assertTrue(json.contains("\"count\":3"));
        // 反序列化字符串 → Long 自动还原
        WithId back = JsonUtils.fromJson("{\"id\":\"1234567890123456789\",\"count\":\"3\"}", WithId.class);
        assertEquals(1234567890123456789L, back.id);
        assertEquals(3, back.count.intValue());
    }

    // ---------- PageParam ----------

    @Test
    void pageParamSemantics() {
        PageParam param = new PageParam();
        assertTrue(param.isPaged());
        param.setPageSize(PageParam.PAGE_SIZE_NONE);
        assertTrue(param.isUnpaged());
    }

    @Test
    void sortablePageParamCarriesSorting() {
        SortablePageParam param = new SortablePageParam();
        param.setSortingField(new SortingField("createTime", SortingField.Order.DESCENDING));
        assertEquals("createTime", param.getSortingField().getFieldName());
        assertEquals(SortingField.Order.DESCENDING, param.getSortingField().getOrder());
    }

    // ---------- EntityDiff ----------

    static class Row {
        Long id;
        String name;

        Row(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Row && ((Row) o).id.equals(this.id) && ((Row) o).name.equals(this.name);
        }

        @Override
        public int hashCode() {
            return id.hashCode() * 31 + name.hashCode();
        }
    }

    @Test
    void entityDiffProducesCreateUpdateDelete() {
        List<Row> oldList = Arrays.asList(new Row(1L, "a"), new Row(2L, "b"), new Row(3L, "c"));
        List<Row> newList = Arrays.asList(new Row(2L, "b"), new Row(3L, "c2"), new Row(4L, "d"));

        EntityDiff diff = EntityDiff.of(oldList, newList, r -> r.id);
        assertEquals(1, diff.getToCreate().size());
        assertEquals(4L, ((Row) diff.getToCreate().get(0)).id);
        assertEquals(1, diff.getToUpdate().size());
        assertEquals(3L, ((Row) diff.getToUpdate().get(0)).id);
        assertEquals(1, diff.getToDelete().size());
        assertEquals(1L, ((Row) diff.getToDelete().get(0)).id);
        assertFalse(diff.isEmpty());
    }

    @Test
    void entityDiffHandlesNullAndIdentical() {
        assertTrue(EntityDiff.of(null, null, r -> r).isEmpty());
        List<Row> same = Collections.singletonList(new Row(1L, "a"));
        assertTrue(EntityDiff.of(same, same, r -> r.id).isEmpty());
    }

    // ---------- LocalDateTimeUtils ----------

    @Test
    void overlapDetection() {
        LocalDateTime d1 = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime d2 = LocalDateTime.of(2026, 1, 10, 0, 0);
        LocalDateTime d3 = LocalDateTime.of(2026, 1, 5, 0, 0);
        LocalDateTime d4 = LocalDateTime.of(2026, 1, 20, 0, 0);
        assertTrue(LocalDateTimeUtils.isOverlap(d1, d2, d3, d4));
        assertFalse(LocalDateTimeUtils.isOverlap(d1, d2, d4, null));
        assertTrue(LocalDateTimeUtils.isOverlap(d1, null, d3, d4));
    }

    @Test
    void intersectionOfRanges() {
        LocalDateTime d1 = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime d2 = LocalDateTime.of(2026, 1, 10, 0, 0);
        LocalDateTime d3 = LocalDateTime.of(2026, 1, 5, 0, 0);
        LocalDateTime d4 = LocalDateTime.of(2026, 1, 20, 0, 0);
        LocalDateTime[] span = LocalDateTimeUtils.intersection(d1, d2, d3, d4);
        assertArrayEquals(new LocalDateTime[]{d3, d2}, span);
        assertNull(LocalDateTimeUtils.intersection(d1, d2, d4, null));
    }

    @Test
    void epochMilliRoundTrip() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0, 0);
        assertEquals(now, LocalDateTimeUtils.fromEpochMilli(LocalDateTimeUtils.toEpochMilli(now)));
    }

    // ---------- ValidationUtils ----------

    static class Form {
        @NotNull(message = "名称不能为空")
        private String name = null;

        @Min(value = 1, message = "数量至少为 1")
        @Max(value = 10, message = "数量最多为 10")
        private Integer count = 99;

        String getName() {
            return name;
        }

        Integer getCount() {
            return count;
        }
    }

    @Test
    void programmaticValidationThrowsBizException() {
        BizException e = assertThrows(BizException.class, () -> ValidationUtils.validate(new Form()));
        assertTrue(e.getMessage().contains("数量") || e.getMessage().contains("名称"));
        assertTrue(e.getErrorCode() == com.sfi.scaffold.common.exception.GlobalErrorCode.BAD_REQUEST);
    }

    @Test
    void validObjectPassesSilently() {
        Form form = new Form();
        form.name = "x";
        form.count = 5;
        ValidationUtils.validate(form);
    }

    // ---------- SpringExpressionUtils ----------

    @Test
    void spelEvaluationWithVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("userId", 42L);
        assertEquals(42L, SpringExpressionUtils.evaluate("#userId", vars));
        assertEquals("u42", SpringExpressionUtils.evaluate("'u' + #userId", vars, String.class));
    }

    @Test
    void spelTemplateEvaluation() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("orderId", "A001");
        assertEquals("订单A001 已发货", SpringExpressionUtils.evaluateTemplate("订单#{#orderId} 已发货", vars));
    }
}
