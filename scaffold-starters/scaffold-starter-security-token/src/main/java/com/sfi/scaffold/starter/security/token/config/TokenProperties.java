package com.sfi.scaffold.starter.security.token.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * token 认证组件配置项，前缀 {@code scaffold.security.token}。
 *
 * @author lianghang
 */
@ConfigurationProperties(prefix = "scaffold.security.token")
public class TokenProperties {

    /** 会话 TTL（秒），默认 7 天 */
    private long ttlSeconds = 7 * 24 * 3600L;

    /** 剩余有效期低于该百分比时自动续期 */
    private int renewThresholdPercent = 20;

    /** 同端互斥：登录是否踢掉同设备旧会话 */
    private boolean exclusiveLogin = false;

    /** 登录 IP 防护（防单 IP 撞库，与账号维度锁定互补） */
    private IpGuard ipGuard = new IpGuard();

    /** 登录图形验证码 */
    private Captcha captcha = new Captcha();

    public static class IpGuard {

        /** 总开关；关闭后仅保留账号维度失败锁定 */
        private boolean enabled = true;

        /** 同 IP 窗口内登录失败上限，达到后拒绝该 IP 的账密登录 */
        private int failLimit = 30;

        /** 失败计数窗口（秒） */
        private int windowSeconds = 600;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getFailLimit() {
            return failLimit;
        }

        public void setFailLimit(int failLimit) {
            this.failLimit = failLimit;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }

    public static class Captcha {

        /** 模式：off=不启用 | always=每次都要 | after_fail=连续失败 failThreshold 次后强制 */
        private String mode = "after_fail";

        /** after_fail 模式的触发阈值（连续失败次数） */
        private int failThreshold = 3;

        /** 验证码有效期（秒） */
        private int ttlSeconds = 120;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public int getFailThreshold() {
            return failThreshold;
        }

        public void setFailThreshold(int failThreshold) {
            this.failThreshold = failThreshold;
        }

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getRenewThresholdPercent() {
        return renewThresholdPercent;
    }

    public void setRenewThresholdPercent(int renewThresholdPercent) {
        this.renewThresholdPercent = renewThresholdPercent;
    }

    public boolean isExclusiveLogin() {
        return exclusiveLogin;
    }

    public void setExclusiveLogin(boolean exclusiveLogin) {
        this.exclusiveLogin = exclusiveLogin;
    }

    public IpGuard getIpGuard() {
        return ipGuard;
    }

    public void setIpGuard(IpGuard ipGuard) {
        this.ipGuard = ipGuard;
    }

    public Captcha getCaptcha() {
        return captcha;
    }

    public void setCaptcha(Captcha captcha) {
        this.captcha = captcha;
    }
}
