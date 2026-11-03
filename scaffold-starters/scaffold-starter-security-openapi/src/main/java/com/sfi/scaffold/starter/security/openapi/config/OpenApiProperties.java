package com.sfi.scaffold.starter.security.openapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * openapi 签名认证组件配置项，前缀 {@code scaffold.security.openapi}。
 *
 * @author lianghang
 */
@ConfigurationProperties(prefix = "scaffold.security.openapi")
public class OpenApiProperties {

    /** 时间戳窗口（秒，正负双向），超窗直接拒绝 */
    private long timestampWindowSeconds = 300;

    public long getTimestampWindowSeconds() {
        return timestampWindowSeconds;
    }

    public void setTimestampWindowSeconds(long timestampWindowSeconds) {
        this.timestampWindowSeconds = timestampWindowSeconds;
    }
}
