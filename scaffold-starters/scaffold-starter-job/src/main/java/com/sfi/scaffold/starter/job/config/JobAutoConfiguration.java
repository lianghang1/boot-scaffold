package com.sfi.scaffold.starter.job.config;

import com.sfi.scaffold.starter.job.JobConfigProvider;
import com.sfi.scaffold.starter.job.JobExecutor;
import com.sfi.scaffold.starter.job.JobRunLogSink;
import com.sfi.scaffold.starter.protection.lock.RedisLockUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;

/**
 * 计划任务自动装配（极简引擎）。
 * 接入 PowerJob 时：server 不引本 starter 的注册逻辑（scaffold.job.engine=none），
 * 由 PowerJob 适配模块接管调度，AbstractJob 子类零改动。
 */
@Configuration
@EnableConfigurationProperties(JobAutoConfiguration.JobProperties.class)
public class JobAutoConfiguration {

    @ConfigurationProperties(prefix = "scaffold.job")
    public static class JobProperties {

        /** simple = 本注册器；none = 接入外部调度平台 */
        private String engine = "simple";

        /** 调度线程池大小 */
        private int poolSize = 4;

        public String getEngine() {
            return engine;
        }

        public void setEngine(String engine) {
            this.engine = engine;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(TaskScheduler.class)
    public TaskScheduler jobTaskScheduler(JobProperties properties) {
        return JobRegistrar.initScheduler(properties.getPoolSize());
    }

    /** 双引擎共用执行包装：无条件注册——engine=none（PowerJob 接管调度）时同样需要 */
    @Bean
    public JobExecutor jobExecutor(ObjectProvider<JobRunLogSink> runLogSink,
                                   ObjectProvider<RedisLockUtil> lockUtil) {
        return new JobExecutor(runLogSink.getIfAvailable(), lockUtil.getIfAvailable());
    }

    @Bean
    public JobRegistrar jobRegistrar(ApplicationContext applicationContext, Environment environment,
                                     TaskScheduler taskScheduler, JobProperties properties,
                                     ObjectProvider<JobConfigProvider> configProvider,
                                     JobExecutor jobExecutor) {
        if ("none".equalsIgnoreCase(properties.getEngine())) {
            // 外部调度平台接管：不注册，运行时管理走平台自带控制台
            return null;
        }
        return new JobRegistrar(applicationContext, environment, taskScheduler,
                configProvider.getIfAvailable(), jobExecutor);
    }
}
