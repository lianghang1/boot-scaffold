package com.sfi.scaffold.starter.tenant.bs;

import cn.zhxu.bs.BeanMeta;
import cn.zhxu.bs.ParamFilter;
import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;
import com.sfi.scaffold.starter.tenant.annotation.TenantIgnore;
import com.sfi.scaffold.starter.tenant.config.TenantProperties;
import com.sfi.scaffold.starter.tenant.core.TenantIgnoreHolder;
import com.sfi.scaffold.query.plan.annotation.TableX;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bean Searcher 通路租户过滤器：检索前探测计划类是否含 {@code tenantId} 字段
 * （Bean Searcher 官方一等能力 ParamFilter，作者 issue I7KE1V 认可的字段探测方案），
 * 含则注入 {@code tenantId = 当前登录租户} 等值条件，join 自动传导主表条件到联表查询。
 *
 * <p><b>放行条件</b>：平台身份（登录身份无 tenantId）、{@code @TenantIgnore} 方法豁免
 * 标记期间、计划类标注 {@code @TenantIgnore}、计划类主表命中
 * {@code scaffold.tenant.ignore-tables} 豁免名单、计划类无 {@code tenantId} 字段
 * （非租户表）。</p>
 *
 * <p>注入为固定条件：外部传入的同名参数（含运算符变体）被覆盖，前端无法篡改租户视角。
 * 作用面为<b>自动注入的检索</b>；{@code QueryUtilsX.doNativeQuery(sql)} 纯 SQL 入口
 * 本就不注入，租户条件由业务写进 SQL。</p>
 *
 * @author lianghang
 */
public class TenantParamFilter implements ParamFilter {

    /** 计划类租户字段名（固定探测名；列名映射由计划类字段声明负责） */
    public static final String TENANT_FIELD = "tenantId";

    private final TenantProperties properties;

    public TenantParamFilter(TenantProperties properties) {
        this.properties = properties;
    }

    @Override
    public <T> Map<String, Object> doFilter(BeanMeta<T> beanMeta, Map<String, Object> paraMap) {
        LoginUser user = LoginContext.currentOrNull();
        String tenantId = user == null ? null : user.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            return paraMap;
        }
        return inject(beanMeta.getBeanClass(), beanMeta.getFieldSet(), paraMap,
                tenantId, TenantIgnoreHolder.isMarked(), properties.getIgnoreTables());
    }

    /**
     * 注入核心（与 BS API 解耦，便于单测）：返回注入后的参数表，不满足注入条件时原表返回。
     */
    static Map<String, Object> inject(Class<?> beanClass, Set<String> fieldSet, Map<String, Object> paraMap,
                                      String tenantId, boolean ignoreMarked, Iterable<String> ignoreTables) {
        if (ignoreMarked || beanClass.isAnnotationPresent(TenantIgnore.class)) {
            return paraMap;
        }
        if (isIgnoredTable(beanClass, ignoreTables)) {
            return paraMap;
        }
        if (fieldSet == null || !fieldSet.contains(TENANT_FIELD)) {
            return paraMap;
        }
        Map<String, Object> params = new HashMap<>(paraMap);
        // 固定条件覆盖：丢弃同字段动态参数的值与运算符变体，前端无法篡改租户视角
        params.entrySet().removeIf(e -> {
            if (e.getValue() instanceof cn.zhxu.bs.param.FieldParam) {
                return TENANT_FIELD.equals(((cn.zhxu.bs.param.FieldParam) e.getValue()).getName());
            }
            String key = e.getKey();
            return key.equals(TENANT_FIELD) || key.equals(TENANT_FIELD + "_op") || key.equals(TENANT_FIELD + "_ic")
                    || key.startsWith(TENANT_FIELD + "[") || key.startsWith(TENANT_FIELD + "_");
        });
        params.put(TENANT_FIELD, tenantId);
        return params;
    }

    /** 豁免名单匹配：计划类主表名（@TableX.name）命中即放行 */
    private static boolean isIgnoredTable(Class<?> beanClass, Iterable<String> ignoreTables) {
        TableX table = beanClass.getAnnotation(TableX.class);
        if (table == null) {
            return false;
        }
        for (String name : ignoreTables) {
            if (table.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
