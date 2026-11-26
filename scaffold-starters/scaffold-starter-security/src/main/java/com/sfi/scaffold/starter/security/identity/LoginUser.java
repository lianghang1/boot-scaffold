package com.sfi.scaffold.starter.security.identity;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 三条安全链认证成功后归一化的登录用户模型。
 *
 * <p>OPERATOR 的 roles/permissions 来自 RBAC；APP 的 scopes 并入 permissions；
 * MEMBER 默认无角色，资源归属校验由业务层按 accountId 过滤——身份与授权解耦，链路只负责认人。</p>
 *
 * @author lianghang
 */
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long accountId;

    private String accountNo;

    private AccountType accountType;

    /** 登录名/应用名，用于日志与审计展示 */
    private String displayName;

    /** 角色（OPERATOR）或 scope（APP），已带 ROLE_ 前缀的角色存 roles，纯权限串存 permissions */
    private Set<String> roles = Collections.emptySet();

    private Set<String> permissions = Collections.emptySet();

    /** 登录来源/接入端类型：由认证阶段填入，如 internal / online / app:{accountNo} */
    private String source;

    /** 物理设备标识：客户端 X-Device-Id，多点登录按此维度管控 */
    private String deviceId;

    /**
     * 租户 ID（null = 平台身份，租户过滤全放行）。
     *
     * <p>由业务登录逻辑查表填入，随认证链装入会话并在请求重建身份时自动就位；
     * 定时任务 / MQ 消费等无 HTTP 场景经 {@code TenantScope.execute} 或
     * {@code LoginContext.set} 携带。scaffold-starter-tenant 在场时作为
     * 三条过滤通路的统一租户来源。</p>
     */
    private String tenantId;

    /**
     * 业务扩展槽：各体系自定义的附加字段（部门、等级、组织编码等），值统一 String。
     *
     * <p>会话经 Redis JSON 序列化存取，Map 值无类型信息（复杂对象读回即退化为
     * LinkedHashMap），故约束 String——业务需要结构化数据时自行存 JSON 串并解析。
     * 框架级字段（如 tenantId）走一线字段，业务差异字段走本槽；随
     * {@code SessionInfo.from/toLoginUser} 互转透传，请求重建身份时不丢失。</p>
     */
    private Map<String, String> attributes = new LinkedHashMap<>();

    /**
     * 系统任务身份工厂：定时任务、消息消费等无 HTTP 请求的后台线程使用。
     *
     * <p>accountId 固定 0（不入库的身份），accountType 取 OPERATOR（内部操作方），
     * permissions 为 {@code *} 全量——后台任务以系统身份执行业务时不受权限拦截，
     * 审计字段 createBy/updateBy 自动落 {@code system}。
     * 经 {@code LoginContext.set} 写入后，TTL 底座保证子线程同样可见。</p>
     *
     * @param operator 操作来源标识，如 job 名（log/审计展示用）
     */
    public static LoginUser system(String operator) {
        LoginUser user = new LoginUser();
        user.setAccountId(0L);
        user.setAccountNo("system");
        user.setAccountType(AccountType.OPERATOR);
        user.setDisplayName("system:" + (operator == null ? "task" : operator));
        user.setPermissions(Collections.singleton("*"));
        user.setSource("job");
        return user;
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role.startsWith("ROLE_") ? role : "ROLE_" + role);
    }

    /**
     * 权限判定：{@code *} 全量放行（超级管理员）；精确匹配；
     * 或持有 {@code system:account:*} 形式的段级通配。任一命中即放行。
     */
    public boolean hasPermission(String permission) {
        if (permissions == null || permission == null) {
            return false;
        }
        if (permissions.contains("*") || permissions.contains(permission)) {
            return true;
        }
        int wildcard = permission.lastIndexOf(':');
        while (wildcard > 0) {
            if (permissions.contains(permission.substring(0, wildcard) + ":*")) {
                return true;
            }
            wildcard = permission.lastIndexOf(':', wildcard - 1);
        }
        return false;
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

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
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
}
