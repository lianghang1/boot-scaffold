package com.sfi.scaffold.starter.tenant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 多租户配置项（{@code scaffold.tenant.*}）。
 *
 * <p>opt-in 语义：{@code enabled} 默认 false，未开启时本 starter 不装配任何过滤组件，
 * 三条通路行为与现状完全一致。</p>
 *
 * @author lianghang
 */
@ConfigurationProperties(prefix = "scaffold.tenant")
public class TenantProperties {

    /** 总开关：默认关闭，开启后按登录身份 tenantId 自动注入过滤条件 */
    private boolean enabled = false;

    /**
     * 豁免表名列表：作用于 Bean Searcher 通路的注入排除（计划类 @TableX.name 命中即不注入）。
     * 参照芋道：字典 / 配置 / 租户映射本身等系统表列入豁免。
     */
    private List<String> ignoreTables = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getIgnoreTables() {
        return ignoreTables;
    }

    public void setIgnoreTables(List<String> ignoreTables) {
        this.ignoreTables = ignoreTables;
    }
}
