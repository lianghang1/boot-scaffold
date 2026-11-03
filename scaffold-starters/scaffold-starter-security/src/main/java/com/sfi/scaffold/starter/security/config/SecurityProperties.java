package com.sfi.scaffold.starter.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全部配置项，前缀 {@code scaffold.security}。
 *
 * <p>边界链只认两类放行来源：代码里的 {@code @Anonymous} 注解（意图跟代码走）
 * 与本配置的 {@code permit-urls}（兜底代码外路径：静态资源、actuator、
 * 三方 jar 的接口）。其余请求一律 {@code authenticated()}，fail-closed。
 * token 会话、防刷、验证码等认证组件的配置在各组件自己的前缀下。</p>
 *
 * @author lianghang
 */
@ConfigurationProperties(prefix = "scaffold.security")
public class SecurityProperties {

    /** 兜底白名单（ant 风格）：注解覆盖不到的路径，如 /actuator/**、/static/** */
    private List<String> permitUrls = new ArrayList<>();

    /** 密码策略（强度校验 + 连续失败锁定） */
    private PasswordPolicy passwordPolicy = new PasswordPolicy();

    public static class PasswordPolicy {

        /** 连续登录失败次数上限，达到即临时锁定 */
        private int maxFailCount = 5;

        /** 锁定时长（秒） */
        private int lockSeconds = 900;

        /** 密码最小长度 */
        private int minLength = 8;

        /** 是否必须同时包含字母和数字 */
        private boolean requireLetterAndDigit = true;

        public int getMaxFailCount() {
            return maxFailCount;
        }

        public void setMaxFailCount(int maxFailCount) {
            this.maxFailCount = maxFailCount;
        }

        public int getLockSeconds() {
            return lockSeconds;
        }

        public void setLockSeconds(int lockSeconds) {
            this.lockSeconds = lockSeconds;
        }

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int minLength) {
            this.minLength = minLength;
        }

        public boolean isRequireLetterAndDigit() {
            return requireLetterAndDigit;
        }

        public void setRequireLetterAndDigit(boolean requireLetterAndDigit) {
            this.requireLetterAndDigit = requireLetterAndDigit;
        }
    }

    public List<String> getPermitUrls() {
        return permitUrls;
    }

    public void setPermitUrls(List<String> permitUrls) {
        this.permitUrls = permitUrls;
    }

    public PasswordPolicy getPasswordPolicy() {
        return passwordPolicy;
    }

    public void setPasswordPolicy(PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }
}
