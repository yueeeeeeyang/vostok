package yueyang.vostok.util.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ConcurrentHashMap;

public final class VKTimes {
    private VKTimes() {
    }

    /**
     * 格式化缓存：key = "pattern\0zoneId"，value = 含时区的 DateTimeFormatter。
     * DateTimeFormatter 线程安全，可安全共享；computeIfAbsent 保证每个 pattern+zone 只构建一次。
     */
    private static final ConcurrentHashMap<String, DateTimeFormatter> FORMAT_CACHE = new ConcurrentHashMap<>();

    /**
     * 解析缓存：key = pattern，value = 不含时区的 DateTimeFormatter（时区在 atZone 阶段应用）。
     */
    private static final ConcurrentHashMap<String, DateTimeFormatter> PARSE_CACHE = new ConcurrentHashMap<>();

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
        // 缓存含时区的格式化器，避免每次调用重新解析 pattern（DateTimeFormatter 线程安全）
        DateTimeFormatter fmt = FORMAT_CACHE.computeIfAbsent(
                p + "\0" + zone.getId(),
                k -> DateTimeFormatter.ofPattern(p).withZone(zone));
        return fmt.format(instant);
    }

    public static Instant parseInstant(String text, ZoneId zoneId, String pattern) {
        if (text == null || text.isBlank()) {
            return null;
        }
        ZoneId zone = zoneId == null ? ZoneId.systemDefault() : zoneId;
        String p = (pattern == null || pattern.isBlank()) ? "yyyy-MM-dd HH:mm:ss" : pattern;
        // 缓存不含时区的格式化器（时区通过 atZone 在解析后应用）
        DateTimeFormatter fmt = PARSE_CACHE.computeIfAbsent(p, DateTimeFormatter::ofPattern);
        // 先尝试含时间的 pattern（如 "yyyy-MM-dd HH:mm:ss"），失败时降级为纯日期 pattern（补零时）
        try {
            return LocalDateTime.parse(text, fmt).atZone(zone).toInstant();
        } catch (DateTimeParseException e) {
            return LocalDate.parse(text, fmt).atStartOfDay(zone).toInstant();
        }
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
