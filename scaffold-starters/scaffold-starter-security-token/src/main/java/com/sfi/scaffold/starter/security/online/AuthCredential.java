package com.sfi.scaffold.starter.security.online;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一授权凭证：登录骨架交给身份源供应商的入参。
 *
 * <p>登录骨架只做一件事——把前端提交的凭证原样转交供应商核验，不解释凭证内容。
 * 因此这里只约定「来源标识 + 一组键值对」，不假设任何协议形态：</p>
 * <ul>
 *     <li>微信/OIDC：{@code code}</li>
 *     <li>飞书/钉钉：{@code code}</li>
 *     <li>CAS：{@code ticket}</li>
 *     <li>SAML2：{@code SAMLResponse}</li>
 * </ul>
 * <p>具体取哪些键、怎么核验，由各 {@link OAuthIdentityProvider} 实现自行决定。</p>
 *
 * @author matrix
 */
public class AuthCredential {

    /** 身份源标识，用于路由到对应的 {@link OAuthIdentityProvider} */
    private final String provider;

    /** 凭证键值对，登录骨架不解释其内容 */
    private final Map<String, String> attributes;

    public AuthCredential(String provider, Map<String, String> attributes) {
        this.provider = provider;
        this.attributes = attributes == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(attributes));
    }

    public String getProvider() {
        return provider;
    }

    /** 取凭证字段（如 code / ticket / SAMLResponse），不存在返回 null。 */
    public String get(String key) {
        return attributes.get(key);
    }

    /** 取凭证字段，不存在返回默认值。 */
    public String get(String key, String defaultValue) {
        String value = attributes.get(key);
        return value == null ? defaultValue : value;
    }

    /** 只读的凭证快照。 */
    public Map<String, String> getAttributes() {
        return attributes;
    }
}
