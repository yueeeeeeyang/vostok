package yueyang.vostok.data.plugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 拦截器注册表。
 */
public final class VKInterceptorRegistry {
    private static final List<VKInterceptor> LIST = new CopyOnWriteArrayList<>();

    private VKInterceptorRegistry() {
    }

    
    public static void register(VKInterceptor interceptor) {
        if (interceptor != null) {
            LIST.add(interceptor);
        }
    }

    
    public static void clear() {
        LIST.clear();
    }

    
    public static List<VKInterceptor> all() {
        return LIST;
    }

    public static boolean hasAny() {
        return !LIST.isEmpty();
    }
}
