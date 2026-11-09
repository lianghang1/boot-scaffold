package com.sfi.scaffold.common;

import com.sfi.scaffold.common.util.DateUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DateUtils 测试：全局格式常量与转换。
 *
 * @author lianghang
 */
class DateUtilsTest {

    @Test
    void formatAndParseRoundtrip() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 30, 22, 45, 0);
        String text = DateUtils.format(time);
        assertEquals("2026-10-30 22:45:00", text);
        assertEquals(time, DateUtils.parseDateTime(text));
    }

    @Test
    void nullAndEmptySafe() {
        assertNull(DateUtils.format((LocalDateTime) null));
        assertNull(DateUtils.parseDateTime(null));
        assertNull(DateUtils.parseDateTime("  "));
        assertNull(DateUtils.formatDate((LocalDate) null));
        assertNull(DateUtils.parseDate(null));
    }

    @Test
    void epochMilliRoundtrip() {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        long milli = DateUtils.toEpochMilli(time);
        assertEquals(time, DateUtils.fromEpochMilli(milli));
    }

    @Test
    void datePatternConstants() {
        assertEquals("yyyy-MM-dd HH:mm:ss", DateUtils.DATE_TIME_PATTERN);
        assertEquals("yyyy-MM-dd", DateUtils.DATE_PATTERN);
        assertEquals("2026-10-30", DateUtils.formatDate(LocalDate.of(2026, 10, 30)));
    }

    @Test
    void javaUtilDateRoundtrip() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 30, 8, 30, 0);
        java.util.Date date = DateUtils.toDate(time);
        assertEquals(time, DateUtils.toLocalDateTime(date));
        assertNull(DateUtils.toDate(null));
        assertNull(DateUtils.toLocalDateTime(null));
    }

    @Test
    void dayBalanceSecondsInDay() {
        long seconds = DateUtils.dayBalanceSeconds();
        assertTrue(seconds > 0 && seconds <= 86400);
    }

    @Test
    void compactFormat() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 30, 22, 45, 9);
        assertEquals("20261030224509", DateUtils.formatCompact(time));
        assertNull(DateUtils.formatCompact((LocalDateTime) null));
    }

    @Test
    void chineseWeek() {
        // 2026-10-30 是星期五
        assertEquals("星期五", DateUtils.chineseWeek(LocalDate.of(2026, 10, 30)));
        assertNull(DateUtils.chineseWeek(null));
    }

    // ---- Date 域（存量桥接） ----

    @Test
    void dateFormatDelegatesToSameFormatters() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 30, 22, 45, 9);
        java.util.Date date = java.util.Date.from(time.atZone(java.time.ZoneId.systemDefault()).toInstant());

        assertEquals("2026-10-30 22:45:09", DateUtils.format(date));
        assertEquals("2026-10-30", DateUtils.formatDate(date));
        assertEquals("20261030224509", DateUtils.formatCompact(date));
        assertEquals("20261030", DateUtils.formatDateCompact(date));

        assertNull(DateUtils.format((java.util.Date) null));
        assertNull(DateUtils.formatDate((java.util.Date) null));
        assertNull(DateUtils.formatCompact((java.util.Date) null));
        assertNull(DateUtils.formatDateCompact((java.util.Date) null));
    }

    @Test
    void dateEpochMilliMatchesLocalDateTimeVersion() {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        java.util.Date date = java.util.Date.from(time.atZone(java.time.ZoneId.systemDefault()).toInstant());

        assertEquals(DateUtils.toEpochMilli(time), DateUtils.toEpochMilli(date));
    }

    @Test
    void offsetDayIsCalendarSemantic() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 30, 22, 45, 0);
        java.util.Date date = java.util.Date.from(time.atZone(java.time.ZoneId.systemDefault()).toInstant());

        java.util.Date plusDay = DateUtils.offsetDay(date, 1);
        assertEquals(LocalDateTime.of(2026, 10, 31, 22, 45, 0), DateUtils.toLocalDateTime(plusDay));

        // 月末跨月：日历语义 +1 天落到下月 1 号同一时刻
        java.util.Date monthEnd = DateUtils.toDate(LocalDateTime.of(2026, 1, 31, 8, 0, 0));
        assertEquals(LocalDateTime.of(2026, 2, 1, 8, 0, 0), DateUtils.toLocalDateTime(DateUtils.offsetDay(monthEnd, 1)));

        // 负数即减
        assertEquals(time, DateUtils.toLocalDateTime(DateUtils.offsetDay(plusDay, -1)));

        assertNull(DateUtils.offsetDay(null, 1));
        assertNull(DateUtils.offsetHour(null, 1));
        assertNull(DateUtils.offsetMinute(null, 1));
    }

    @Test
    void offsetHourAndMinute() {
        java.util.Date date = DateUtils.toDate(LocalDateTime.of(2026, 10, 30, 22, 45, 0));

        assertEquals(LocalDateTime.of(2026, 10, 31, 0, 45, 0),
                DateUtils.toLocalDateTime(DateUtils.offsetHour(date, 2)));
        assertEquals(LocalDateTime.of(2026, 10, 30, 20, 45, 0),
                DateUtils.toLocalDateTime(DateUtils.offsetHour(date, -2)));
        assertEquals(LocalDateTime.of(2026, 10, 31, 1, 15, 0),
                DateUtils.toLocalDateTime(DateUtils.offsetMinute(date, 150)));
    }

    @Test
    void dayBoundaries() {
        java.util.Date date = DateUtils.toDate(LocalDateTime.of(2026, 10, 30, 15, 30, 45));

        assertEquals(LocalDateTime.of(2026, 10, 30, 0, 0, 0), DateUtils.toLocalDateTime(DateUtils.beginOfDay(date)));
        LocalDateTime end = DateUtils.toLocalDateTime(DateUtils.endOfDay(date));
        assertEquals(LocalDateTime.of(2026, 10, 30, 23, 59, 59, 999_000_000), end);

        assertNull(DateUtils.beginOfDay(null));
        assertNull(DateUtils.endOfDay(null));
    }
}
