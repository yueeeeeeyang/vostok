package yueyang.vostok.config.validate;

import yueyang.vostok.config.exception.VKConfigErrorCode;
import yueyang.vostok.config.exception.VKConfigException;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 内置 {@link VKConfigValidator} 工厂方法集合。
 * <p>
 * 通过静态方法组合出常用校验器，可与 {@code VostokConfig.registerValidator} 配合使用。
 * 所有校验器对 key 不存在或值为空白字符串的情况均不报错（视为"未配置，不需要校验"），
 * 需同时检查存在性时请与 {@link #required} 或 {@link #notBlank} 组合。
 */
public final class VKConfigValidators {

    private VKConfigValidators() {
    }

    // ── 存在性校验 ────────────────────────────────────────────────────────────

    /**
     * 校验一组 key 均存在且不为空白字符串。
     *
     * @param keys 必填的 config key 列表
     */
    public static VKConfigValidator required(String... keys) {
        String[] checked = keys == null ? new String[0] : keys;
        return view -> {
            for (String key : checked) {
                String v = view.get(key);
                if (v == null || v.isBlank()) {
                    throw new VKConfigException(VKConfigErrorCode.VALIDATION_ERROR,
                            "Required config key is missing or blank: " + key);
                }
            }
        };
    }

    /**
     * {@link #required} 的语义别名，更明确地表达"值不能为空白"。
     *
     * @param keys 必须不为空白的 config key 列表
     */
    public static VKConfigValidator notBlank(String... keys) {
        return required(keys);
    }

    // ── 数值范围校验 ──────────────────────────────────────────────────────────

    /**
     * 校验整数 key 的值在 [min, max] 范围内（闭区间）。
     * key 不存在或值为空时跳过校验。
     */
    public static VKConfigValidator intRange(String key, int min, int max) {
        return view -> {
            String raw = view.get(key);
            if (raw == null || raw.isBlank()) {
                return;
            }
            int value;
            try {
                value = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new VKConfigException(VKConfigErrorCode.VALIDATION_ERROR,
                        "Config key is not integer: " + key + "=" + raw);
            }
            if (value < min || value > max) {
                throw new VKConfigException(VKConfigErrorCode.VALIDATION_ERROR,
                        "Config key out of range: " + key + "=" + value +
                        ", expected [" + min + "," + max + "]");
            }
        };
    }

    /**
     * 校验 key 的整数值必须为正数（> 0）。
     * key 不存在或值为空时跳过校验。
     */
    public static VKConfigValidator positiveInt(String key) {
        return view -> {
            String raw = view.get(key);
            if (raw == null || raw.isBlank()) {
                return;
            }
            int value;
            try {
                value = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new VKConfigException(VKConfigErrorCode.VALIDATION_ERROR,
                        "Config key is not integer: " + key + "=" + raw);
            }
            if (value <= 0) {
                throw new VKConfigException(VKConfigErrorCode.VALIDATION_ERROR,
                        "Config key must be positive: " + key + "=" + value);
            }
        };
    }

    /**
     * 校验 key 的整数值是合法端口号（[1, 65535]）。
     * key 不存在或值为空时跳过校验。
     */
    public static VKConfigValidator portRange(String key) {
        return intRange(key, 1, 65535);
    }

    // ── 枚举值校验 ────────────────────────────────────────────────────────────

    /**
     * 校验 key 的值必须是 allowedValues 中的一个（大小写不敏感）。
     * key 不存在或值为空时跳过校验。
     *
     * @param key           要校验的 config key
     * @param allowedValues 允许的合法值列表
     */
    public static VKConfigValidator oneOf(String key, String... allowedValues) {
        Objects.requireNonNull(allowedValues, "allowedValues");
        // 构建大小写不敏感的合法值集合
        Set<String> allowed = Arrays.stream(allowedValues)
                .filter(Objects::nonNull)
                .map(v -> v.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        String allowedDesc = Arrays.toString(allowedValues);
        return view -> {
            String raw = view.get(key);
            if (raw == null || raw.isBlank()) {
                return;
            }
            if (!allowed.contains(raw.trim().toLowerCase(Locale.ROOT))) {
                throw new VKConfigException(VKConfigErrorCode.VALIDATION_ERROR,
                        "Config key value not allowed: " + key + "=" + raw +
                        ", allowed=" + allowedDesc);
            }
        };
    }

    // ── 格式校验 ──────────────────────────────────────────────────────────────

    /**
     * 校验 key 的值匹配指定正则表达式（{@code matches} 全串匹配）。
     * key 不存在或值为空时跳过校验。
     *
     * @param key   要校验的 config key
     * @param regex 正则表达式（{@link java.util.regex.Pattern} 语法）
     */
    public static VKConfigValidator pattern(String key, String regex) {
        Objects.requireNonNull(regex, "regex");
        Pattern p = Pattern.compile(regex);
        return view -> {
            String raw = view.get(key);
            if (raw == null || raw.isBlank()) {
                return;
            }
            if (!p.matcher(raw).matches()) {
                throw new VKConfigException(VKConfigErrorCode.VALIDATION_ERROR,
                        "Config key format invalid: " + key + "=" + raw + ", regex=" + regex);
            }
        };
    }

    /**
     * 校验 key 的值是合法的 URL（http / https / 自定义 scheme 均可，
     * 通过 {@link URI} 语法检查，不做网络连通性测试）。
     * key 不存在或值为空时跳过校验。
     */
    public static VKConfigValidator url(String key) {
        return view -> {
            String raw = view.get(key);
            if (raw == null || raw.isBlank()) {
                return;
            }
            try {
                URI uri = URI.create(raw.trim());
                if (uri.getScheme() == null) {
                    throw new IllegalArgumentException("missing scheme");
                }
            } catch (IllegalArgumentException e) {
                throw new VKConfigException(VKConfigErrorCode.VALIDATION_ERROR,
                        "Config key is not a valid URL: " + key + "=" + raw);
            }
        };
    }

    // ── 跨字段校验 ────────────────────────────────────────────────────────────

    /**
     * 自定义跨字段校验：当 predicate 返回 false 时抛出校验异常。
     *
     * @param name      校验规则名称（用于错误消息）
     * @param predicate 接收完整 {@link VKConfigView} 的断言函数
     * @param message   断言失败时的描述信息
     */
    public static VKConfigValidator cross(String name, Predicate<VKConfigView> predicate, String message) {
        Objects.requireNonNull(predicate, "predicate");
        return view -> {
            if (!predicate.test(view)) {
                throw new VKConfigException(VKConfigErrorCode.VALIDATION_ERROR,
                        "Cross-field validation failed(" + name + "): " + message);
            }
        };
    }
}
