package com.sfi.scaffold.starter.security.online;

/**
 * 外部身份源供应商 SPI：把登录骨架转交的授权凭证核验为统一外部身份。
 *
 * <p>职责边界：本接口只负责「凭证 → 外部身份」这一步。授权凭证怎么拿到（网页授权跳转、
 * 小程序静默登录、扫码等）属于授权域，不在登录骨架内；登录骨架把凭证原样装进
 * {@link AuthCredential} 转交，不解释其内容。供应商自行决定取哪些键、如何核验。</p>
 *
 * <p>身份源可插拔，未配置任何实现时登录接口显式报「未接入登录源」而非静默放行。</p>
 *
 * @author lianghang
 */
public interface OAuthIdentityProvider {

    /** 身份源标识：wechat-mp / wechat-mini / feishu / dingtalk … */
    String provider();

    /**
     * 核验授权凭证并换取统一外部身份。
     *
     * @param credential 登录骨架转交的授权凭证（来源标识 + 一组键值对）
     * @return 统一外部身份，核验失败返回 null 或抛业务异常
     * @throws com.sfi.scaffold.common.exception.BizException 核验失败（凭证无效等）
     */
    ExternalIdentity resolve(AuthCredential credential);
}
