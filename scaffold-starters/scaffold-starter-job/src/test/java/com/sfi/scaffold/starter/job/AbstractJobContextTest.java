package com.sfi.scaffold.starter.job;

import com.sfi.scaffold.common.constant.TraceConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AbstractJob 上下文填充测试：执行前写入任务专属 traceId，
 * 执行成功/失败后 finally 强制清理。
 *
 * <p>无 Spring 上下文：JobRunRecorder 未安装时走单机退化路径（不落日志、不加锁）。</p>
 *
 * @author lianghang
 */
class AbstractJobContextTest {

    private static final ThreadLocal<String> capturedTraceId = new ThreadLocal<>();

    @AfterEach
    void tearDown() {
        // 防用例失败残留影响其他测试
        MDC.remove(TraceConstants.TRACE_ID_KEY);
        capturedTraceId.remove();
    }

    @Test
    void shouldFillJobTraceIdDuringExecute() {
        DemoJob job = new DemoJob();
        job.run();

        String traceId = capturedTraceId.get();
        assertNotNull(traceId, "执行期间应有任务专属 traceId");
        assertTrue(traceId.startsWith("job-demojob-"), "实际 traceId=" + traceId);
    }

    @Test
    void shouldClearTraceIdAfterSuccess() {
        new DemoJob().run();
        assertNull(MDC.get(TraceConstants.TRACE_ID_KEY), "执行后 traceId 应清理");
    }

    @Test
    void shouldClearTraceIdEvenWhenJobFails() {
        new FailingJob().run();
        assertNull(MDC.get(TraceConstants.TRACE_ID_KEY), "失败路径同样应清理 traceId");
    }

    /** 标注 @Job 的示例任务：捕获执行期间的上下文快照 */
    @Job(name = "DemoJob", cron = "0 0 0 * * ?", lockLeaseSeconds = 0)
    static class DemoJob extends AbstractJob {

        @Override
        protected void doExecute(JobContext context) {
            capturedTraceId.set(MDC.get(TraceConstants.TRACE_ID_KEY));
        }
    }

    @Job(name = "FailingJob", cron = "0 0 0 * * ?", lockLeaseSeconds = 0)
    static class FailingJob extends AbstractJob {

        @Override
        protected void doExecute(JobContext context) {
            throw new IllegalStateException("模拟任务失败");
        }
    }
}
