package com.sfi.scaffold.query.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态检索配置项。
 *
 * @author lianghang
 */
@ConfigurationProperties(prefix = "scaffold.search")
public class QueryProperties {

    /**
     * 是否启用动态检索（QueryUtilsX / QueryPlanX）
     */
    private boolean enabled = true;

    /**
     * QueryPlanX 计划类扫描包（启动时自动注册到 QueryPlanRegistryX）
     */
    private List<String> planPackages = new ArrayList<>();

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
}
