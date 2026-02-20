package yueyang.vostok.util.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class VKTimes {
    private VKTimes() {
    }

    public static long nowEpochMs() {
        return System.currentTimeMillis();
    }

    public static long nowEpochSec() {
        return Instant.now().getEpochSecond();
    }

    public static String formatInstant(Instant instant, ZoneId zoneId, String pattern) {
        if (instant == null) {
            return null;
        }
        ZoneId zone = zoneId == null ? ZoneId.systemDefault() : zoneId;
        String p = (pattern == null || pattern.isBlank()) ? "yyyy-MM-dd HH:mm:ss" : pattern;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(p).withZone(zone);
        return fmt.format(instant);
    }

    public static Instant parseInstant(String text, ZoneId zoneId, String pattern) {
        if (text == null || text.isBlank()) {
            return null;
        }
        ZoneId zone = zoneId == null ? ZoneId.systemDefault() : zoneId;
        String p = (pattern == null || pattern.isBlank()) ? "yyyy-MM-dd HH:mm:ss" : pattern;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(p);
        LocalDateTime local = LocalDateTime.parse(text, fmt);
        return local.atZone(zone).toInstant();
    }

    public static Instant startOfDay(Instant instant, ZoneId zoneId) {
        if (instant == null) {
            return null;
        }
        ZoneId zone = zoneId == null ? ZoneId.systemDefault() : zoneId;
        LocalDate date = instant.atZone(zone).toLocalDate();
        return date.atStartOfDay(zone).toInstant();
    }

    public static Instant endOfDay(Instant instant, ZoneId zoneId) {
        if (instant == null) {
            return null;
        }
        ZoneId zone = zoneId == null ? ZoneId.systemDefault() : zoneId;
        LocalDate date = instant.atZone(zone).toLocalDate();
        return date.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);
    }
}
