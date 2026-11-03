package com.sfi.scaffold.starter.job;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * 任务执行上下文：本次触发信息，由基类构造后传入 doExecute。
 */
public class JobContext {

    private String jobName;

    private LocalDateTime fireTime;

    /** 本次执行的记录 ID（业务自定义 JobRunLogSink 落库时回写，框架默认无 Sink 时为 null） */
    private Long runLogId;

    /** 任务参数：simple 引擎来自 scaffold.job.jobs.<jobKey>.params，外部引擎来自平台下发 */
    private Map<String, String> params = Collections.emptyMap();

    /** 取任务参数 */
    public String getParam(String key) {
        return params.get(key);
    }

    /** 取任务参数，缺省返回默认值 */
    public String getParam(String key, String defaultValue) {
        return params.getOrDefault(key, defaultValue);
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params == null ? Collections.emptyMap() : params;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public LocalDateTime getFireTime() {
        return fireTime;
    }

    public void setFireTime(LocalDateTime fireTime) {
        this.fireTime = fireTime;
    }

    public Long getRunLogId() {
        return runLogId;
    }

    public void setRunLogId(Long runLogId) {
        this.runLogId = runLogId;
    }
}
