package com.sfi.scaffold.query;

/**
 * 检索拦截器 SPI：QueryUtilsX 门面的 before/after 扩展点。
 *
 * <p>实现为 Spring Bean 即自动注册（List 注入）。用于租户/部门数据权限
 * 在报表侧过滤、检索审计等横切能力。</p>
 *
 * @author lianghang
 */
public interface QueryInterceptor {

    /**
     * 检索执行前回调。
     */
    void before(QueryMeta meta);

    /**
     * 检索执行后回调（异常时不回调）。
     *
     * @param rowCount 结果行数；count 类操作为 -1
     */
    void after(QueryMeta meta, long elapsedMillis, long rowCount);
}
