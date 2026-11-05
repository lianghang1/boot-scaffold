package com.sfi.scaffold.starter.protection.identity;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

/**
 * 请求身份解析：登录用户 &gt; 网关注入的 X-Account-Id &gt; 客户端 IP。
 *
 * <p>真实登录身份（token 会话装入 LoginContext）最可信，优先；网关 header 仅作
 * 「网关已清洗身份」部署模式的回落；IP 是最后兜底。带前缀（acct:/hdr:/ip:）避免
 * 不同来源的同一字面值互相污染幂等/限流键。</p>
 *
 * @author lianghang
 */
public class RequestIdentity {

    private final LoginIdentityLookup loginLookup;

    public RequestIdentity(LoginIdentityLookup loginLookup) {
        this.loginLookup = loginLookup;
    }

    /** 解析请求身份；请求本身无来源信息时返回 null（调用方自行放行）。 */
    public String resolve(HttpServletRequest request) {
        if (loginLookup != null) {
            Long accountId = loginLookup.currentAccountId();
            if (accountId != null) {
                return "acct:" + accountId;
            }
        }
        String header = request.getHeader("X-Account-Id");
        if (header != null && !header.isEmpty()) {
            return "hdr:" + header;
        }
        String ip = clientIp(request);
        return ip == null ? null : "ip:" + ip;
    }

    /** 客户端 IP：X-Forwarded-For 首段 &gt; X-Real-IP &gt; remoteAddr。 */
    public static String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RequestIdentity
                && Objects.equals(loginLookup == null, ((RequestIdentity) o).loginLookup == null);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(loginLookup == null);
    }
}
