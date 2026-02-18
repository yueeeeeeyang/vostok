package yueyang.vostok.config.validate;

import yueyang.vostok.config.exception.VKConfigErrorCode;
import yueyang.vostok.config.exception.VKConfigException;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class VKConfigValidators {
    private VKConfigValidators() {
    }

    public static VKConfigValidator required(String... keys) {
        String[] required = keys == null ? new String[0] : keys;
        return view -> {
            for (String key : required) {
                String v = view.get(key);
                if (v == null || v.isBlank()) {
                    throw new VKConfigException(VKConfigErrorCode.VALIDATION_ERROR,
                            "Required config key is missing: " + key);
                }
            }
        };
    }

    public static VKConfigValidator intRange(String key, int min, int max) {
        return view -> {
            String raw = view.get(key);
            if (raw == null || raw.isBlank()) {
                return;
            }
            int value;
            try {
                value = Integer.parseInt(raw.trim());
            } catch (Exception e) {
                throw new VKConfigException(VKConfigErrorCode.VALIDATION_ERROR,
                        "Config key is not integer: " + key + "=" + raw);
            }
            if (value < min || value > max) {
                throw new VKConfigException(VKConfigErrorCode.VALIDATION_ERROR,
                        "Config key out of range: " + key + "=" + value + ", expected [" + min + "," + max + "]");
            }
        };
    }

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
