package com.sfi.scaffold.starter.security.token;

import com.sfi.scaffold.starter.security.identity.AccountType;
import com.sfi.scaffold.starter.security.identity.LoginUser;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Redis 会话内容，即 token 对应的服务端会话实体。
 *
 * <p>采用不透明 token 方案（无 JWT），踢人、续期、在线用户查询、同端互斥等管控能力
 * 全部基于本对象实现——会话状态收敛在服务端，才有真正的下线控制权。</p>
 *
 * @author lianghang
 */
public class SessionInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long accountId;

    private String accountNo;

    private String accountType;

    private String displayName;

    private Set<String> roles = Collections.emptySet();

    private Set<String> permissions = Collections.emptySet();

    /** 登录来源/接入端类型，如 internal / online / app:{accountNo} */
    private String source;

    /** 物理设备标识：客户端 X-Device-Id，多点登录按此维度管控 */
    private String deviceId;

    /** 租户 ID（null = 平台身份），随会话存取，与 LoginUser.tenantId 同源透传 */
    private String tenantId;

    /** 业务扩展槽（值统一 String），与 LoginUser.attributes 同源透传 */
    private Map<String, String> attributes = new LinkedHashMap<>();

    /** 登录时间（epoch millis） */
    private long loginTime;

    public static SessionInfo from(LoginUser user) {
        SessionInfo s = new SessionInfo();
        s.accountId = user.getAccountId();
        s.accountNo = user.getAccountNo();
        s.accountType = user.getAccountType() == null ? null : user.getAccountType().name();
        s.displayName = user.getDisplayName();
        s.roles = user.getRoles();
        s.permissions = user.getPermissions();
        s.source = user.getSource();
        s.deviceId = user.getDeviceId();
        s.tenantId = user.getTenantId();
        s.attributes = user.getAttributes() == null ? Collections.emptyMap() : user.getAttributes();
        s.loginTime = System.currentTimeMillis();
        return s;
    }

    public LoginUser toLoginUser() {
        LoginUser u = new LoginUser();
        u.setAccountId(accountId);
        u.setAccountNo(accountNo);
        u.setAccountType(accountType == null ? null : AccountType.valueOf(accountType));
        u.setDisplayName(displayName);
        u.setRoles(roles);
        u.setPermissions(permissions);
        u.setSource(source);
        u.setDeviceId(deviceId);
        u.setTenantId(tenantId);
        u.setAttributes(attributes);
        return u;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes == null ? Collections.emptyMap() : attributes;
    }

    public long getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(long loginTime) {
        this.loginTime = loginTime;
    }
}
