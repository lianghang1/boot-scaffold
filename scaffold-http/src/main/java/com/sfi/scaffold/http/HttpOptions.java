package com.sfi.scaffold.http;

/**
 * HTTP 客户端配置（纯 Java，无 Spring 依赖）。
 *
 * <p>业务自行 new 一个 {@link ScaffoldHttpClient} 并复用；配置通过本对象
 * 构造传入，不再读 yml、不注册 Bean。默认值即Reasonable Defaults，
 * 需要调整时用 setter 覆盖。</p>
 *
 * @author lianghang
 */
public class HttpOptions {

    /** 建连超时（毫秒） */
    private int connectTimeoutMs = 5_000;

    /** 读超时（毫秒） */
    private int readTimeoutMs = 10_000;

    /** 幂等请求最大尝试次数（含首次） */
    private int retryMaxAttempts = 2;

    /** 重试退避间隔（毫秒） */
    private long retryBackoffMs = 500;

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }
}
