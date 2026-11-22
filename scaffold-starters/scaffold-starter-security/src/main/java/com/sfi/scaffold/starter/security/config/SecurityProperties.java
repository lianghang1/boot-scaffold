package com.sfi.scaffold.starter.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全部配置项，前缀 {@code scaffold.security}。
 *
 * <p>边界链只认两类放行来源：代码里的 {@code @Anonymous} 注解（意图跟代码走）
 * 与本配置的 {@code permit-urls}（兜底代码外路径：静态资源、三方 jar 的接口）。
 * 其余请求一律 {@code authenticated()}，fail-closed。
 * token 会话、防刷、验证码等认证组件的配置在各组件自己的前缀下。</p>
 *
 * @author lianghang
 */
@ConfigurationProperties(prefix = "scaffold.security")
public class SecurityProperties {

    /** 兜底白名单（ant 风格）：注解覆盖不到的路径，如 /static/** */
    private List<String> permitUrls = new ArrayList<>();

    /**
     * URL 权限严格模式：
     * <ul>
     *   <li>{@code false}（默认，本地开发友好）：URL 未命中任何权限规则时放行，
     *       等价于隐式一条「登录即可」，无需给每个接口配 pattern；</li>
     *   <li>{@code true}（生产）：URL 未命中任何权限规则时直接 403，
     *       强制每个受控端点都在菜单里补齐 api_pattern，fail-closed。</li>
     * </ul>
     * 无论取值，{@code @Anonymous} 接口与 {@code permit-urls} 白名单始终放行，
     * 命中的规则正常鉴权——开关只改「无规则命中」这一条兜底路径。
     */
    private boolean urlPermissionStrict = false;

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

    public boolean isUrlPermissionStrict() {
        return urlPermissionStrict;
    }

    public void setUrlPermissionStrict(boolean urlPermissionStrict) {
        this.urlPermissionStrict = urlPermissionStrict;
    }

    public PasswordPolicy getPasswordPolicy() {
        return passwordPolicy;
    }

    public void setPasswordPolicy(PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }
}
