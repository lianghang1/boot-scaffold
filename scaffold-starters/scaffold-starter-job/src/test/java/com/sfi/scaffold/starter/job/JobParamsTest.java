package com.sfi.scaffold.starter.job;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 任务参数传递测试：配置解析（simple 引擎经 JobRegistrar 注入）与 JobContext 透传。
 *
 * <p>无 Spring 上下文：Binder 直解 MockEnvironment，attachParams 模拟注册行为。</p>
 *
 * @author lianghang
 */
class JobParamsTest {

    /** 捕获执行期上下文 */
    static JobContext captured;

    @Job(name = "param demo", cron = "0 0 0 * * ?")
    static class DemoJob extends AbstractJob {

        @Override
        protected void doExecute(JobContext context) {
            captured = context;
        }
    }

    @Test
    void shouldPassParamsToJobContext() {
        Map<String, String> params = new HashMap<>();
        params.put("retainDays", "90");
        params.put("scene", "order");

        DemoJob job = new DemoJob();
        job.attachParams(params);
        job.run();

        assertEquals("90", captured.getParam("retainDays"));
        assertEquals("order", captured.getParam("scene"));
        assertEquals("7", captured.getParam("absent", "7"), "缺省参数应返回默认值");
        assertNull(captured.getParam("absent"));
    }

    @Test
    void shouldDefaultToEmptyParams() {
        captured = null;
        new DemoJob().run();

        assertEquals("", captured.getParam("anything", ""), "未挂参数时 getParam 带默认值仍可用");
    }

    @Test
    void shouldResolveParamsFromEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("scaffold.job.jobs.param-demo.params.retainDays", "30")
                .withProperty("scaffold.job.jobs.param-demo.params.scene", "sms");
        Map<String, String> params = org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bind("scaffold.job.jobs.param-demo.params",
                        org.springframework.boot.context.properties.bind.Bindable.mapOf(String.class, String.class))
                .orElse(java.util.Collections.emptyMap());

        DemoJob job = new DemoJob();
        job.attachParams(params);
        job.run();

        assertEquals("30", captured.getParam("retainDays"));
        assertEquals("sms", captured.getParam("scene"));
    }
}
