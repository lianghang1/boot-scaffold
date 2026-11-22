package com.sfi.scaffold.query.core;

/**
 * 线程级逻辑删除豁免标记：{@code @IncludeDeleted} 方法切面标记、检索过滤器读取。
 *
 * <p>独立于租户豁免持有器——两个维度正交，可各自单独或同时标记。按深度计数而非
 * 布尔量，嵌套的豁免方法返回时只减一层，不会误清外层仍需生效的豁免。</p>
 *
 * @author lianghang
 */
public final class IncludeDeletedHolder {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private IncludeDeletedHolder() {
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
