package com.sfi.scaffold.starter.job;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * micrometer 实现：仅在类路径存在 micrometer-core 时由条件装配加载。
 *
 * <p>本类是 starter-job 内唯一允许 import micrometer 的地方。</p>
 *
 * @author lianghang
 */
public class MicrometerJobMetrics implements JobMetrics {

    private final MeterRegistry registry;

    /** registry 可为 null（micrometer 在场但无注册表 bean），静默 */
    public MicrometerJobMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 执行指标：scaffold.job.executions（outcome 维度）+ scaffold.job.duration */
    @Override
    public void record(String jobKey, String outcome, long costMillis) {
        if (registry == null) {
            return;
        }
        registry.counter(com.sfi.scaffold.common.constant.MetricsNames.JOB_EXECUTIONS, "job", jobKey, "outcome", outcome).increment();
        Timer.builder(com.sfi.scaffold.common.constant.MetricsNames.JOB_DURATION).tag("job", jobKey).register(registry)
                .record(costMillis, TimeUnit.MILLISECONDS);
    }
}
