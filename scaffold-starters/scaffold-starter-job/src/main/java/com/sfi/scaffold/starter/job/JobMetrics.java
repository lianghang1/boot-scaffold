package com.sfi.scaffold.starter.job;

/**
 * 任务执行指标门面：隔离 micrometer 类型，observability 不在场时静默。
 *
 * <p>注册器只依赖本接口——保证 starter-job 单独使用（无 web/actuator 传递的
 * micrometer-core）时类加载安全，由条件装配决定绑定 micrometer 实现或 noop。</p>
 *
 * @author lianghang
 */
public interface JobMetrics {

    /** 记录一次任务执行结果 */
    void record(String jobKey, String outcome, long costMillis);

    /** 空实现：无指标注册表时使用 */
    JobMetrics NOOP = (jobKey, outcome, costMillis) -> { };
}
