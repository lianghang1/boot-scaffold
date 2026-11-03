package com.sfi.scaffold.starter.security.openapi;

import com.sfi.scaffold.common.exception.GlobalErrorCode;
import com.sfi.scaffold.common.util.ServletUtils;
import com.sfi.scaffold.common.util.StringUtils;
import com.sfi.scaffold.starter.security.filter.CachedBodyRequestWrapper;
import com.sfi.scaffold.starter.security.identity.AccountType;
import com.sfi.scaffold.starter.security.identity.IdentityContextSupport;
import com.sfi.scaffold.starter.security.identity.LoginUser;
import com.sfi.scaffold.starter.security.openapi.config.OpenApiProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * HMAC 签名认证过滤器：签名要素为 appKey + timestamp + nonce + body 摘要。
 *
 * <p>防重放两层：时间戳窗口必查；Redis 在场时叠加 nonce 一次性
 * （setIfAbsent），缺席时退化为纯时间戳窗口。fail closed：凭证提供者
 * 缺席或签名不符一律拒绝；多文件请求（multipart）不参与签名直接放行。
 * 认证成功归一化为 APP 账户上下文；无效时静默继续链，由授权段裁决。</p>
 *
 * @author lianghang
 */
public class OpenApiSignFilter extends OncePerRequestFilter {

    public static final String HEADER_APP_KEY = "X-App-Key";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGN = "X-Sign";

    private static final String NONCE_KEY = com.sfi.scaffold.common.constant.RedisKeys.OPENAPI_NONCE;

    private final AppSecretProvider appSecretProvider;
    private final OpenApiProperties properties;
    /** 可选：null 时无 nonce 防重放，仅时间戳窗口 */
    private final StringRedisTemplate redisTemplate;

    public OpenApiSignFilter(AppSecretProvider appSecretProvider,
                             OpenApiProperties properties,
                             StringRedisTemplate redisTemplate) {
        this.appSecretProvider = appSecretProvider;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            if (request.getContentType() != null
                    && request.getContentType().toLowerCase().contains("multipart")) {
                // 文件上传不参与签名，直接放行（如需保护可按业务扩展）
                chain.doFilter(request, response);
                return;
            }
            CachedBodyRequestWrapper wrapped = new CachedBodyRequestWrapper(request);
            if (authenticate(wrapped, response)) {
                chain.doFilter(wrapped, response);
            }
        } finally {
            IdentityContextSupport.clear();
        }
    }

    private boolean authenticate(CachedBodyRequestWrapper request,
                                 HttpServletResponse response) throws IOException {
        String appKey = request.getHeader(HEADER_APP_KEY);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);
        String sign = request.getHeader(HEADER_SIGN);

        if (anyBlank(appKey, timestamp, nonce, sign)) {
            return reject(response, GlobalErrorCode.UNAUTHORIZED);
        }
        // 1. 应用存在性
        AppIdentity app = appSecretProvider.load(appKey);
        if (app == null) {
            return reject(response, GlobalErrorCode.UNAUTHORIZED);
        }
        // 2. 时间戳窗口
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return reject(response, GlobalErrorCode.UNAUTHORIZED);
        }
        long windowMillis = properties.getTimestampWindowSeconds() * 1000L;
        if (Math.abs(System.currentTimeMillis() - ts) > windowMillis) {
            return reject(response, GlobalErrorCode.UNAUTHORIZED);
        }
        // 3. nonce 一次性（Redis 在场时）
        if (redisTemplate != null) {
            Boolean first = redisTemplate.opsForValue()
                    .setIfAbsent(NONCE_KEY + nonce, "1", windowMillis, TimeUnit.MILLISECONDS);
            if (first == null || !first) {
                return reject(response, GlobalErrorCode.CONFLICT);
            }
        }
        // 4. 签名校验
        String body = request.getBodyString();
        String signBase = appKey + "\n" + timestamp + "\n" + nonce + "\n"
                + SignUtils.sha256Hex(body == null ? "" : body);
        String expected = SignUtils.hmacSha256Hex(signBase, app.getAppSecret());
        if (!SignUtils.constantTimeEquals(expected, sign)) {
            return reject(response, GlobalErrorCode.UNAUTHORIZED);
        }

        // 5. 归一化为 APP 账户上下文
        LoginUser user = new LoginUser();
        user.setAccountId(app.getAccountId());
        user.setAccountNo(appKey);
        user.setAccountType(AccountType.APP);
        user.setDisplayName(app.getAppName());
        user.setPermissions(app.getScopes() == null ? Collections.<String>emptySet() : app.getScopes());
        user.setDevice("openapi:" + appKey);
        IdentityContextSupport.applyUser(user);
        return true;
    }

    private boolean anyBlank(String... values) {
        for (String v : values) {
            if (StringUtils.isBlank(v)) {
                return true;
            }
        }
        return false;
    }

    private boolean reject(HttpServletResponse response, GlobalErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        ServletUtils.writeJSON(response, com.sfi.scaffold.common.result.R.fail(errorCode));
        return false;
    }
}
