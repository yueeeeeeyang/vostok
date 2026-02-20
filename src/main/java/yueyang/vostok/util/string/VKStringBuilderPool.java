package yueyang.vostok.util.string;

import java.util.function.Function;

public final class VKStringBuilderPool {
    private static final int DEFAULT_CAPACITY = 256;
    private static final int MAX_RETAIN_CAPACITY = 8192;
    private static final ThreadLocal<StringBuilder> HOLDER = ThreadLocal.withInitial(() -> new StringBuilder(DEFAULT_CAPACITY));

    private VKStringBuilderPool() {
    }

    public static <T> T withBuilder(Function<StringBuilder, T> fn) {
        if (fn == null) {
            throw new IllegalArgumentException("Function is null");
        }
        StringBuilder sb = HOLDER.get();
        sb.setLength(0);
        try {
            return fn.apply(sb);
        } finally {
            if (sb.capacity() > MAX_RETAIN_CAPACITY) {
                HOLDER.set(new StringBuilder(DEFAULT_CAPACITY));
            } else {
                sb.setLength(0);
            }
        }
    }
}
