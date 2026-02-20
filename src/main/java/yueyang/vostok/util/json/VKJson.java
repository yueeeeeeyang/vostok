package yueyang.vostok.util.json;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class VKJson {
    private static final String BUILTIN_PROVIDER = "builtin";
    private static final Map<String, VKJsonProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static final AtomicReference<VKJsonProvider> CURRENT = new AtomicReference<>();

    static {
        VKJsonProvider builtin = new VKBuiltinJsonProvider();
        PROVIDERS.put(normalizeName(builtin.name()), builtin);
        CURRENT.set(builtin);
    }

    private VKJson() {
    }

    public static String toJson(Object obj) {
        return current().toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return current().fromJson(json, type);
    }

    public static void registerProvider(VKJsonProvider provider) {
        Objects.requireNonNull(provider, "VKJsonProvider is null");
        String name = normalizeName(provider.name());
        PROVIDERS.put(name, provider);
    }

    public static void use(String providerName) {
        String name = normalizeName(providerName);
        VKJsonProvider provider = PROVIDERS.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("JSON provider not found: " + providerName);
        }
        CURRENT.set(provider);
    }

    public static void resetDefault() {
        use(BUILTIN_PROVIDER);
    }

    public static String currentProviderName() {
        return current().name();
    }

    public static Set<String> providerNames() {
        return Set.copyOf(PROVIDERS.keySet());
    }

    private static VKJsonProvider current() {
        VKJsonProvider provider = CURRENT.get();
        if (provider == null) {
            throw new IllegalStateException("No JSON provider is active");
        }
        return provider;
    }

    private static String normalizeName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("JSON provider name is blank");
        }
        return providerName.trim().toLowerCase(Locale.ROOT);
    }
}
