package com.sfi.scaffold.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 集合实体 diff：按业务键对比新旧两份列表，产出增/改/删三段结果，
 * 批量 save 场景（先删后插的低效写法替代）直接消费。
 *
 * <p>「改」的判定基于 {@code Objects.equals}——实体需正确实现 equals，
 * 或传入的元素本身是可比较的值对象。</p>
 *
 * @author lianghang
 */
public final class EntityDiff {

    private final List<Object> toCreate;
    private final List<Object> toUpdate;
    private final List<Object> toDelete;

    private EntityDiff(List<Object> toCreate, List<Object> toUpdate, List<Object> toDelete) {
        this.toCreate = toCreate;
        this.toUpdate = toUpdate;
        this.toDelete = toDelete;
    }

    public List<Object> getToCreate() {
        return toCreate;
    }

    public List<Object> getToUpdate() {
        return toUpdate;
    }

    public List<Object> getToDelete() {
        return toDelete;
    }

    public boolean isEmpty() {
        return toCreate.isEmpty() && toUpdate.isEmpty() && toDelete.isEmpty();
    }

    /**
     * 对比新旧列表。
     *
     * @param oldList 数据库现状（可 null）
     * @param newList 目标状态（可 null，等价空列表）
     * @param keyFn   业务键提取（如 id / bizNo）
     * @param <K>     键类型
     * @param <T>     元素类型
     */
    public static <K, T> EntityDiff of(Collection<T> oldList, Collection<T> newList,
                                       Function<T, K> keyFn) {
        Map<K, T> oldMap = index(oldList, keyFn);
        Map<K, T> newMap = index(newList, keyFn);

        List<Object> toCreate = new ArrayList<>();
        List<Object> toUpdate = new ArrayList<>();
        List<Object> toDelete = new ArrayList<>();

        for (Map.Entry<K, T> entry : newMap.entrySet()) {
            T old = oldMap.get(entry.getKey());
            if (old == null) {
                toCreate.add(entry.getValue());
            } else if (!Objects.equals(old, entry.getValue())) {
                toUpdate.add(entry.getValue());
            }
        }
        for (Map.Entry<K, T> entry : oldMap.entrySet()) {
            if (!newMap.containsKey(entry.getKey())) {
                toDelete.add(entry.getValue());
            }
        }
        return new EntityDiff(toCreate, toUpdate, toDelete);
    }

    private static <K, T> Map<K, T> index(Collection<T> list, Function<T, K> keyFn) {
        Map<K, T> map = new HashMap<>();
        if (CollUtils.isNotEmpty(list)) {
            for (T item : list) {
                map.put(keyFn.apply(item), item);
            }
        }
        return map;
    }
}
