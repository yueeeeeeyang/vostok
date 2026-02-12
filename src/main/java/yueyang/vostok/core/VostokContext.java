package yueyang.vostok.core;

import yueyang.vostok.util.VKAssert;

import java.util.function.Supplier;

/**
 * Vostok 运行期上下文载体（当前仅包含数据源上下文）。
 */
public final class VostokContext {
    private final String dataSourceName;

    private VostokContext(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public static VostokContext capture() {
        return new VostokContext(VostokRuntime.DS_CONTEXT.get());
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void run(Runnable action) {
        VKAssert.notNull(action, "Runnable is null");
        runInternal(() -> {
            action.run();
            return null;
        });
    }

    public <T> T call(Supplier<T> supplier) {
        VKAssert.notNull(supplier, "Supplier is null");
        return runInternal(supplier);
    }

    private <T> T runInternal(Supplier<T> supplier) {
        String prev = VostokRuntime.DS_CONTEXT.get();
        if (dataSourceName == null) {
            VostokRuntime.DS_CONTEXT.remove();
        } else {
            VostokRuntime.DS_CONTEXT.set(dataSourceName);
        }
        try {
            return supplier.get();
        } finally {
            if (prev == null) {
                VostokRuntime.DS_CONTEXT.remove();
            } else {
                VostokRuntime.DS_CONTEXT.set(prev);
            }
        }
    }
}
