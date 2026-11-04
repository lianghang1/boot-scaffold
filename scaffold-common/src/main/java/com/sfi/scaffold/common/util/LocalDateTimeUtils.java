package com.sfi.scaffold.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * LocalDateTime 区间运算：重叠判定、交集、epoch 毫秒互转。
 *
 * <p>重叠判定按「左闭右开」语义（start <= other.end && other.start <= end），
 * 端点相接不算重叠之外的边界争议由调用方裁断；end 为 null 视为无上界。</p>
 *
 * @author lianghang
 */
public final class LocalDateTimeUtils {

    private LocalDateTimeUtils() {
    }

    /** 两区间是否重叠（任一端点为 null 视为无界）。 */
    public static boolean isOverlap(LocalDateTime start1, LocalDateTime end1,
                                    LocalDateTime start2, LocalDateTime end2) {
        return !before(start1, end2) && !before(start2, end1);
    }

    /** 两区间交集；无交集返回 null。 */
    public static LocalDateTime[] intersection(LocalDateTime start1, LocalDateTime end1,
                                               LocalDateTime start2, LocalDateTime end2) {
        if (!isOverlap(start1, end1, start2, end2)) {
            return null;
        }
        LocalDateTime start = max(start1, start2);
        LocalDateTime end = min(end1, end2);
        if (end != null && start != null && end.isBefore(start)) {
            return null;
        }
        return new LocalDateTime[]{start, end};
    }

    /** LocalDateTime → epoch 毫秒（系统时区）。 */
    public static long toEpochMilli(LocalDateTime dateTime) {
        Objects.requireNonNull(dateTime, "dateTime");
        return dateTime.toInstant(ZoneId.systemDefault().getRules()
                .getOffset(dateTime)).toEpochMilli();
    }

    /** epoch 毫秒 → LocalDateTime（系统时区）。 */
    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    /** a 是否严格早于 b（b 为 null 视为无上界 → 恒不早于）。 */
    private static boolean before(LocalDateTime a, LocalDateTime b) {
        return b != null && a != null && a.isAfter(b);
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : (a.isAfter(b) ? a : b);
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : (a.isBefore(b) ? a : b);
    }
}
