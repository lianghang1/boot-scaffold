package com.sfi.scaffold.starter.job.config;

import com.sfi.scaffold.starter.job.AbstractJob;
import com.sfi.scaffold.starter.job.JobConfigProvider;
import com.sfi.scaffold.starter.job.JobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 任务注册器（极简引擎）：扫描 AbstractJob Bean 注册到显式线程池，
 * 支持「表配置覆盖代码默认值 + 运行时改 cron/启停 + 立即触发」。
 *
 * <p>回退链：外部配置（{@link JobConfigProvider}，如 sys_job 表）&gt; 代码注解默认值；
 * 无提供方（server-lite 零依赖形态）自动回退纯代码驱动。</p>
 *
 * <p>接入 PowerJob 时 {@code scaffold.job.engine=none}，注册源替换为 PowerJob
 * Server 配置，业务任务类零改动。</p>
 *
 * @author lianghang
 */
public class JobRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(JobRegistrar.class);

    private final ApplicationContext applicationContext;
    private final Environment environment;
    private final TaskScheduler taskScheduler;
    private final JobConfigProvider configProvider;
    /** 双引擎共用执行包装：安装 Recorder 环境 + 执行 + 记指标（锁/日志/指标语义与引擎无关） */
    private final JobExecutor jobExecutor;

    /** 运行中任务的调度句柄（jobKey → future），运行时刷新按 key 取消重排 */
    private final Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();
    private final Map<String, AbstractJob> jobsByJobKey = new ConcurrentHashMap<>();
    private final Map<String, String> codeCrons = new ConcurrentHashMap<>();

    public JobRegistrar(ApplicationContext applicationContext, Environment environment,
                        TaskScheduler taskScheduler, JobConfigProvider configProvider,
                        JobExecutor jobExecutor) {
        this.applicationContext = applicationContext;
        this.environment = environment;
        this.taskScheduler = taskScheduler;
        this.configProvider = configProvider;
        this.jobExecutor = jobExecutor;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, AbstractJob> jobs = applicationContext.getBeansOfType(AbstractJob.class);
        int registered = 0;
        for (AbstractJob job : jobs.values()) {
            try {
                AbstractJob.JobMetadata metadata = job.metadata();
                String jobKey = metadata.jobKey;
                // 代码默认值（占位符经 Environment 解析）
                String cron = environment.resolvePlaceholders(metadata.cron);
                boolean enabled = Boolean.parseBoolean(
                        environment.resolvePlaceholders(String.valueOf(metadata.enabled)));
                codeCrons.put(jobKey, cron);
                jobsByJobKey.put(jobKey, job);
                // 外部配置覆盖（表配置 > 代码默认）
                String source = "code";
                if (configProvider != null) {
                    Optional<JobConfigProvider.JobOverride> override = configProvider.find(jobKey);
                    if (override.isPresent()) {
                        cron = environment.resolvePlaceholders(override.get().getCron());
                        enabled = override.get().isEnabled();
                        source = "db";
                    }
                }
                if (!enabled) {
                    log.info("[job] {} 已下线（enabled=false，来源 {}）", metadata.jobName, source);
                    continue;
                }
                job.attachParams(resolveParams(jobKey));
                schedule(job, jobKey, cron);
                registered++;
                log.info("[job] 注册任务: {} cron={} (来源: {})", metadata.jobName, cron, source);
            } catch (Exception e) {
                log.error("[job] 注册失败: {}", job.getClass().getName(), e);
            }
        }
        log.info("[job] 任务注册完成，共 {} 个（发现 {} 个）", registered, jobs.size());
    }

    /**
     * 运行时应用新配置（改 cron / 启停 / 参数），立即生效不重启。
     *
     * @return false 表示任务不在本实例注册表（未注册或外部引擎接管），调用方不应落表
     */
    public boolean apply(String jobKey, String cron, boolean enabled) {
        AbstractJob job = jobsByJobKey.get(jobKey);
        if (job == null) {
            return false;
        }
        ScheduledFuture<?> old = scheduled.remove(jobKey);
        if (old != null) {
            old.cancel(false);
        }
        if (enabled) {
            // 参数一并重读：运行时改配置全量生效，不留注册时快照
            job.attachParams(resolveParams(jobKey));
            schedule(job, jobKey, cron);
        }
        log.info("[job] 配置已应用: {} cron={} enabled={}", jobKey, cron, enabled);
        return true;
    }

    /** 立即触发一次（异步，与定时执行走同一包装，锁/日志语义一致） */
    public boolean triggerNow(String jobKey) {
        AbstractJob job = jobsByJobKey.get(jobKey);
        if (job == null) {
            return false;
        }
        taskScheduler.schedule(() -> jobExecutor.execute(job), java.time.Instant.now());
        return true;
    }

    /** 注册表快照：管理端列表合并视图用（enabled = 是否在调度中） */
    public List<JobInfo> views() {
        List<JobInfo> list = new ArrayList<>();
        for (Map.Entry<String, AbstractJob> entry : jobsByJobKey.entrySet()) {
            AbstractJob.JobMetadata metadata = entry.getValue().metadata();
            list.add(new JobInfo(entry.getKey(), metadata.jobName,
                    entry.getValue().getClass().getName(),
                    codeCrons.get(entry.getKey()), scheduled.containsKey(entry.getKey())));
        }
        return list;
    }

    /** 解析任务参数：scaffold.job.jobs.<jobKey>.params（未配置返回空 Map） */
    private Map<String, String> resolveParams(String jobKey) {
        return Binder.get(environment)
                .bind("scaffold.job.jobs." + jobKey + ".params", Bindable.mapOf(String.class, String.class))
                .orElse(java.util.Collections.emptyMap());
    }

    private void schedule(AbstractJob job, String jobKey, String cron) {
        ScheduledFuture<?> future = taskScheduler.schedule(() -> jobExecutor.execute(job), new CronTrigger(cron));
        scheduled.put(jobKey, future);
    }

    /** 注册表条目视图：codeCron 为代码注解默认值，enabled 为当前是否在调度中 */
    public static class JobInfo {

        private final String jobKey;
        private final String jobName;
        private final String className;
        private final String codeCron;
        private final boolean enabled;

        public JobInfo(String jobKey, String jobName, String className,
                       String codeCron, boolean enabled) {
            this.jobKey = jobKey;
            this.jobName = jobName;
            this.className = className;
            this.codeCron = codeCron;
            this.enabled = enabled;
        }

        public String getJobKey() {
            return jobKey;
        }

        public String getJobName() {
            return jobName;
        }

        public String getClassName() {
            return className;
        }

        public String getCodeCron() {
            return codeCron;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    /** 显式线程池：默认 4 线程，任务多时经 scaffold.job.pool-size 调整 */
    public static ThreadPoolTaskScheduler buildScheduler(int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("scaffold-job-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(t -> LoggerFactory.getLogger("scaffold-job-error")
                .error("[job] 调度器捕获未处理异常", t));
        return scheduler;
    }

    public static TaskScheduler initScheduler(int poolSize) {
        ThreadPoolTaskScheduler scheduler = buildScheduler(poolSize);
        scheduler.initialize();
        return scheduler;
    }
}
