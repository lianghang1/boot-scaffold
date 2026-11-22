package com.sfi.scaffold.starter.job;

import com.sfi.scaffold.starter.protection.lock.RedisLockUtil;

/**
 * 双引擎共用的任务执行包装：安装运行环境（Recorder TTL）→ 执行 → 卸载。
 *
 * <p>内置引擎（{@code JobRegistrar} 调度）与 PowerJob 适配（{@code PowerJobJobAdapter}
 * 触发）都必须经由本类执行 {@link AbstractJob}，保证锁、执行日志、指标三项横切语义
 * 与调度引擎无关——绕过本类直接 {@code job.run()} 会导致该引擎下 Recorder 缺位：
 * 锁被模板跳过（多实例重复执行风险）、执行日志不落库。</p>
 *
 * <p>依赖全部可缺省：无 JPA 不落执行日志，无 Redis 不加锁（tryLock 返回伪持有者），
 * 与整体降级语义一致。</p>
 *
 * @author lianghang
 */
public class JobExecutor {

    private final JobRunLogSink runLogSink;
    private final RedisLockUtil lockUtil;

    public JobExecutor(JobRunLogSink runLogSink, RedisLockUtil lockUtil) {
        this.runLogSink = runLogSink;
        this.lockUtil = lockUtil;
    }

    /**
     * 在当前线程安装运行环境并执行任务：调用方（调度线程或 PowerJob worker 线程）
     * 无需感知 Recorder TTL 语义；同步执行，异常原样上抛交给调度方处置
     * （PowerJob 标记实例失败并按控制台配置重试）。
     */
    public void execute(AbstractJob job) {
        JobRunRecorder.install(runLogSink, lockUtil);
        try {
            job.run();
        } finally {
            JobRunRecorder.uninstall();
        }
    }
}
