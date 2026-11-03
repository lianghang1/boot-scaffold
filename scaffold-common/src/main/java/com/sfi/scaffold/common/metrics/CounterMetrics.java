package com.sfi.scaffold.common.metrics;

/**
 * 计数器指标门面：隔离 micrometer 类型，无指标体系时静默。
 *
 * <p>starter 内需要打点的组件只依赖本接口——保证 optional 依赖（micrometer-core）
 * 不在场时类加载安全，由条件装配决定绑定 micrometer 实现或 noop。</p>
 *
 * @author lianghang
 */
public interface CounterMetrics {

    /** 计数 +1，tags 按 key/value 依次成对传入 */
    void increment(String name, String... tags);

    /** 空实现：无指标体系时使用 */
    CounterMetrics NOOP = (name, tags) -> { };
}
