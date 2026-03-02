package yueyang.vostok.config.validate;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 配置快照的只读视图，供 {@link VKConfigValidator} 在校验阶段使用。
 * <p>
 * 提供了常用类型转换方法，方便编写复杂的 cross-field 校验逻辑，
 * 避免在 Validator 中手动解析字符串类型。
 */
public class VKConfigView {

    private final Map<String, String> data;

    public VKConfigView(Map<String, String> data) {
        this.data = data;
    }

    // ── 基础访问 ─────────────────────────────────────────────────────────────

    /** 获取原始字符串值，key 不存在时返回 null。 */
    public String get(String key) {
        return data.get(key);
    }

    /** 判断 key 是否存在（即使值为空字符串也视为存在）。 */
    public boolean has(String key) {
        return data.containsKey(key);
    }

    /** 返回所有 key 的集合（只读）。 */
    public Set<String> keys() {
        return data.keySet();
    }

    /** 返回底层完整快照 Map（只读）。 */
    public Map<String, String> asMap() {
        return data;
    }

    // ── 类型转换访问 ─────────────────────────────────────────────────────────

    /**
     * 获取整数值；key 不存在或无法解析时返回 defaultValue。
     * 解析前自动 trim。
     */
    public int getInt(String key, int defaultValue) {
        String value = data.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取长整数值；key 不存在或无法解析时返回 defaultValue。
     */
    public long getLong(String key, long defaultValue) {
        String value = data.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取双精度浮点值；key 不存在或无法解析时返回 defaultValue。
     */
    public double getDouble(String key, double defaultValue) {
        String value = data.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取布尔值；key 不存在或无法识别时返回 defaultValue。
     * <p>
     * 识别 {@code true/1/yes/on}（不区分大小写）为 true，
     * {@code false/0/no/off} 为 false，其余返回 defaultValue。
     */
    public boolean getBool(String key, boolean defaultValue) {
        String value = data.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v)) {
            return true;
        }
        if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "off".equals(v)) {
            return false;
        }
        return defaultValue;
    }

    /**
     * 获取字符串列表。
     * <p>
     * 查找策略（与 VostokConfig.getList 保持一致）：
     * <ol>
     *   <li>若 key 存在，按 {@code ,} 拆分并 trim，过滤空串</li>
     *   <li>否则尝试索引格式：{@code key[0]}、{@code key[1]}……直到 key 不存在为止</li>
     * </ol>
     *
     * @return 不可变列表；key 不存在时返回空列表
     */
    public List<String> getList(String key) {
        String direct = data.get(key);
        if (direct != null) {
            if (direct.isBlank()) {
                return List.of();
            }
            return Arrays.stream(direct.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; ; i++) {
            String v = data.get(key + "[" + i + "]");
            if (v == null) break;
            out.add(v);
        }
        return out;
    }
}
