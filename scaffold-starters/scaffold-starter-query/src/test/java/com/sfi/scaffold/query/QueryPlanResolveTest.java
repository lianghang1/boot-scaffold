package com.sfi.scaffold.query;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.query.plan.annotation.QueryPlanX;
import com.sfi.scaffold.query.planpck.OrderPlan;
import com.sfi.scaffold.query.planpck.UserPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link QueryUtilsX#resolvePlan} 端点白名单解析测试。
 *
 * @author lianghang
 */
class QueryPlanResolveTest {

    @QueryPlanX({UserPlan.class, OrderPlan.class})
    static class DemoController {
    }

    static class PlainController {
    }

    @Test
    void shouldResolveBySimpleNameIgnoreCase() {
        assertEquals(UserPlan.class, QueryUtilsX.resolvePlan(new DemoController(), "userplan"));
        assertEquals(OrderPlan.class, QueryUtilsX.resolvePlan(DemoController.class, "OrderPlan"));
    }

    @Test
    void shouldRejectUnknownPlan() {
        assertThrows(BizException.class, () -> QueryUtilsX.resolvePlan(new DemoController(), "OtherPlan"));
    }

    @Test
    void shouldRejectControllerWithoutDeclaration() {
        assertThrows(BizException.class, () -> QueryUtilsX.resolvePlan(new PlainController(), "UserPlan"));
    }
}
