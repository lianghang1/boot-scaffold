package com.sfi.scaffold.starter.data.jpa.logicdelete;

import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 逻辑删除字段访问器：按固定字段名 {@code deleted} 反射读写，供不依赖 BaseEntity
 * 的写侧（打删除标记）与 findById 结果比对共用。
 *
 * <p>与租户同构——字段不在基类，由需要的实体自行声明；框架只认固定字段名，
 * 沿类继承链向上探测并缓存。实体无该字段视为非逻辑删除表，读返回 false、
 * 写为空操作，绝不强行注入字段。</p>
 *
 * @author lianghang
 */
public final class DeletedFieldAccessor {

    /** 逻辑删除字段名（全平台统一约定，与 query 通路 DeletedParamFilter 一致） */
    public static final String DELETED_FIELD = "deleted";

    private static final Map<Class<?>, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private DeletedFieldAccessor() {
    }

    /** 是否已逻辑删除；无 deleted 字段的实体恒为 false。 */
    public static boolean isDeleted(Object entity) {
        if (entity == null) {
            return false;
        }
        Field field = resolve(entity.getClass());
        if (field == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(field.get(entity));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("读取 deleted 字段失败：" + entity.getClass().getName(), e);
        }
    }

    /** 打逻辑删除标记；无 deleted 字段的实体为空操作。 */
    public static void markDeleted(Object entity) {
        if (entity == null) {
            return;
        }
        Field field = resolve(entity.getClass());
        if (field == null) {
            return;
        }
        try {
            field.set(entity, Boolean.TRUE);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("写入 deleted 字段失败：" + entity.getClass().getName(), e);
        }
    }

    /** 实体是否含 deleted 字段（按类缓存）。 */
    public static boolean hasField(Class<?> entityClass) {
        return resolve(entityClass) != null;
    }

    /** 沿继承链探测 deleted 字段（CGLIB 代理下取用户类）。 */
    public static Field resolve(Class<?> entityClass) {
        Class<?> userClass = ClassUtils.getUserClass(entityClass);
        return FIELD_CACHE.computeIfAbsent(userClass, DeletedFieldAccessor::scan);
    }

    private static Field scan(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (DELETED_FIELD.equals(f.getName())) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        return null;
    }
}
