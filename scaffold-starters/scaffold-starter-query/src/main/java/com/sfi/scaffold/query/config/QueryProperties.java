package com.sfi.scaffold.query.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态检索配置项。
 *
 * @author lianghang
 */
@ConfigurationProperties(prefix = "scaffold.query")
public class QueryProperties {

    /**
     * 是否启用动态检索（QueryUtilsX / QueryPlanX）
     */
    private boolean enabled = true;

    /**
     * QueryPlanX 计划类扫描包（启动时自动注册到 QueryPlanRegistryX）
     */
    private List<String> planPackages = new ArrayList<>();

    /**
     * 非分页查询结果行数上限，超过即拒绝执行（防全表拉爆内存）
     */
    private int maxRows = 10000;

    /**
     * 分页页大小硬上限
     */
    private int maxPageSize = 200;

    /**
     * 慢检索日志阈值（毫秒），0 或负数表示关闭
     */
    private long slowSqlMillis = 1000;

    /**
     * 严格字段校验：动态条件/排序字段必须是检索类上显式声明的字段，未声明直接拒绝
     */
    private boolean strictFields = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getPlanPackages() {
        return planPackages;
    }

    public void setPlanPackages(List<String> planPackages) {
        this.planPackages = planPackages;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public long getSlowSqlMillis() {
        return slowSqlMillis;
    }

    public void setSlowSqlMillis(long slowSqlMillis) {
        this.slowSqlMillis = slowSqlMillis;
    }

    public boolean isStrictFields() {
        return strictFields;
    }

    public void setStrictFields(boolean strictFields) {
        this.strictFields = strictFields;
    }
}
