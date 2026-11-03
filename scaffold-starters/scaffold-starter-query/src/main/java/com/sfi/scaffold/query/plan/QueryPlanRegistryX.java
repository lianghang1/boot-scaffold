package com.sfi.scaffold.query.plan;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QueryPlanX 注册表：计划名 → 计划类。
 *
 * <p>启动时由自动配置扫描 {@code scaffold.search.plan-packages} 注册，
 * 也可手动调用 {@link #register(Class)}。
 *
 * @author lianghang
 */
public final class QueryPlanRegistryX {

    private static final Map<String, Class<?>> PLANS = new ConcurrentHashMap<>();

    private QueryPlanRegistryX() {
    }

    public static void register(Class<?> planClass) {
        com.sfi.scaffold.query.plan.annotation.QueryPlanX plan =
                planClass.getAnnotation(com.sfi.scaffold.query.plan.annotation.QueryPlanX.class);
        if (plan == null) {
            throw new IllegalArgumentException("类未标注 @QueryPlanX: " + planClass.getName());
        }
        if (!plan.name().isEmpty()) {
            PLANS.put(plan.name(), planClass);
        }
    }

    public static Class<?> get(String planName) {
        return PLANS.get(planName);
    }

    public static Class<?> require(String planName) {
        Class<?> planClass = PLANS.get(planName);
        if (planClass == null) {
            throw new IllegalArgumentException("未注册的 QueryPlanX: " + planName
                    + "（已注册: " + PLANS.keySet() + "）");
        }
        return planClass;
    }

    public static void clear() {
        PLANS.clear();
    }
}
