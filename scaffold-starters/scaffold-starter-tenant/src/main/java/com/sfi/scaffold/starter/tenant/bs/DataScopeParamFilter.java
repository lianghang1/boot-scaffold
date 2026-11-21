package com.sfi.scaffold.starter.tenant.bs;

import cn.zhxu.bs.BeanMeta;
import cn.zhxu.bs.ParamFilter;
import com.sfi.scaffold.common.datascope.DataScopeDecision;
import com.sfi.scaffold.common.datascope.DataScopeHolder;
import com.sfi.scaffold.starter.tenant.core.TenantIgnoreHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bean Searcher 通路数据权限过滤器（与 Jpa 通路共用同一份归约决策）。
 *
 * <p>检索前从 {@link DataScopeHolder} 读当前登录身份的 {@link DataScopeDecision}，
 * 探测计划类是否含 {@code deptId} / {@code createBy} 字段，按决策注入固定条件：</p>
 * <ul>
 *   <li>all=true 或决策为空（平台身份/系统任务）→ 放行不注入；</li>
 *   <li>deptIds 非空且计划类含 deptId → 注入 {@code deptId IN (...)}；</li>
 *   <li>self=true 且计划类含 createBy → 注入 {@code createBy = 当前审计人}；</li>
 *   <li>两者并存 → 两条条件同时注入（检索侧为 AND；要做 OR 语义请用 orFilters
 *       或归属 jpa 通路——列表检索场景通常按部门主维度过滤即可）。</li>
 * </ul>
 *
 * <p>注入为固定条件：外部传入的同名参数被覆盖，前端无法篡改数据视角；未声明相应字段的
 * 计划类（非受控表）不受影响。{@code @TenantIgnore} 豁免期间同样放行数据权限过滤。</p>
 *
 * <p>放本模块（tenant）是因为它同时依赖 security / query / common——数据权限归属
 * scaffold-starter-data-jpa，但 query 通路过滤器需要 ParamFilter（query）+ 决策持有器（common），
 * 与租户过滤器并列装配，避免 data-jpa 与 query 互相依赖。</p>
 *
 * @author lianghang
 */
public class DataScopeParamFilter implements ParamFilter {

    /** 计划类部门字段名 */
    public static final String DEPT_FIELD = "deptId";
    /** 计划类创建人字段名 */
    public static final String CREATOR_FIELD = "createBy";

    @Override
    public <T> Map<String, Object> doFilter(BeanMeta<T> beanMeta, Map<String, Object> paraMap) {
        DataScopeDecision decision = DataScopeHolder.get();
        if (decision == null || decision.isUnrestricted() || TenantIgnoreHolder.isMarked()) {
            return paraMap;
        }
        Set<String> fields = beanMeta.getFieldSet();
        if (fields == null) {
            return paraMap;
        }
        Map<String, Object> params = new HashMap<>(paraMap);
        boolean changed = false;
        if (!decision.getDeptIds().isEmpty() && fields.contains(DEPT_FIELD)) {
            overrideHard(params, DEPT_FIELD);
            params.put(DEPT_FIELD, decision.getDeptIds());
            changed = true;
        }
        if (decision.isSelf() && fields.contains(CREATOR_FIELD)) {
            String owner = com.sfi.scaffold.starter.data.jpa.audit.ScaffoldAuditorHolder.get();
            if (owner != null && !owner.isEmpty()) {
                overrideHard(params, CREATOR_FIELD);
                params.put(CREATOR_FIELD, owner);
                changed = true;
            }
        }
        return changed ? params : paraMap;
    }

    /** 丢弃同字段的动态参数与运算符变体，前端无法覆盖固定条件。 */
    private static void overrideHard(Map<String, Object> params, String name) {
        params.entrySet().removeIf(e -> {
            if (e.getValue() instanceof cn.zhxu.bs.param.FieldParam) {
                return name.equals(((cn.zhxu.bs.param.FieldParam) e.getValue()).getName());
            }
            String key = e.getKey();
            return key.equals(name) || key.equals(name + "_op") || key.equals(name + "_ic")
                    || key.startsWith(name + "[") || key.startsWith(name + "_");
        });
    }
}
