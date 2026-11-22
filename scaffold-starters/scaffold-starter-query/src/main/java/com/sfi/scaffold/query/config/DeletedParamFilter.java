package com.sfi.scaffold.query.config;

import cn.zhxu.bs.BeanMeta;
import cn.zhxu.bs.ParamFilter;
import com.sfi.scaffold.query.core.IncludeDeletedHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bean Searcher 通路逻辑删除过滤器：检索前探测检索类是否含 {@code deleted} 字段，
 * 含则注入固定条件 {@code deleted = false}，未映射该字段的计划类/VO 不受影响
 * （解决"逻辑删除不是每张表都有"）。
 *
 * <p>注入为固定条件：前端传入的同名字段（值、运算符、忽略大小写、数组/区间变体）
 * 一律丢弃，无法篡改绕过——想看已删除数据只能服务端标注 {@code @IncludeDeleted}。</p>
 *
 * <p>{@code @IncludeDeleted} 豁免期、或检索类无 {@code deleted} 字段时原样放行。</p>
 *
 * @author lianghang
 */
public class DeletedParamFilter implements ParamFilter {

    /** 逻辑删除字段名（全平台统一约定） */
    public static final String DELETED_FIELD = "deleted";

    @Override
    public <T> Map<String, Object> doFilter(BeanMeta<T> beanMeta, Map<String, Object> paraMap) {
        if (IncludeDeletedHolder.isMarked()) {
            return paraMap;
        }
        Set<String> fields = beanMeta.getFieldSet();
        if (fields == null || !fields.contains(DELETED_FIELD)) {
            return paraMap;
        }
        Map<String, Object> params = new HashMap<>(paraMap);
        // 丢弃同字段的动态参数与运算符变体，前端无法覆盖固定条件
        params.entrySet().removeIf(e -> {
            if (e.getValue() instanceof cn.zhxu.bs.param.FieldParam) {
                return DELETED_FIELD.equals(((cn.zhxu.bs.param.FieldParam) e.getValue()).getName());
            }
            String key = e.getKey();
            return key.equals(DELETED_FIELD) || key.equals(DELETED_FIELD + "_op")
                    || key.equals(DELETED_FIELD + "_ic")
                    || key.startsWith(DELETED_FIELD + "[") || key.startsWith(DELETED_FIELD + "_");
        });
        params.put(DELETED_FIELD, false);
        return params;
    }
}
