package com.sfi.scaffold.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * micrometer 实现：仅在类路径存在 micrometer-core 时由条件装配加载。
 *
 * <p>本类是 common 内唯一允许 import micrometer 的地方。</p>
 *
 * @author lianghang
 */
public class MicrometerCounterMetrics implements CounterMetrics {

    private final MeterRegistry registry;

    /** registry 可为 null（micrometer 在场但无注册表 bean），静默 */
    public MicrometerCounterMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 计数 +1 */
    @Override
    public void increment(String name, String... tags) {
        if (registry == null) {
            return;
        }
        registry.counter(name, tags).increment();
    }
}
