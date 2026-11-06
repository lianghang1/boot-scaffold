package com.sfi.scaffold.starter.tenant.core;

/**
 * 线程级租户豁免标记：{@code @TenantIgnore} 方法切面标记、过滤组件查询。
 *
 * <p>按深度计数而非布尔量——嵌套的豁免方法返回时只减一层，
 * 不会误清外层仍需生效的豁免。</p>
 *
 * @author lianghang
 */
public final class TenantIgnoreHolder {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private TenantIgnoreHolder() {
    }

    public static void mark() {
        Integer current = DEPTH.get();
        DEPTH.set(current == null ? 1 : current + 1);
    }

    public static boolean isMarked() {
        Integer current = DEPTH.get();
        return current != null && current > 0;
    }

    public static void clear() {
        Integer current = DEPTH.get();
        if (current == null) {
            return;
        }
        if (current <= 1) {
            DEPTH.remove();
        } else {
            DEPTH.set(current - 1);
        }
    }
}
