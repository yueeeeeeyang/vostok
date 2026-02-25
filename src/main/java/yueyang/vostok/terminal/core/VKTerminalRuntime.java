package yueyang.vostok.terminal.core;

import yueyang.vostok.terminal.VKTerminalConfig;
import yueyang.vostok.terminal.VKTerminalTheme;

public final class VKTerminalRuntime {
    private static final VKTerminalRuntime INSTANCE = new VKTerminalRuntime();
    private static final Object LOCK = new Object();

    private volatile boolean initialized;
    private volatile VKTerminalConfig config = new VKTerminalConfig();
    private volatile VKTerminalTheme theme = VKTerminalTheme.defaults();

    private VKTerminalRuntime() {
    }

    public static VKTerminalRuntime getInstance() {
        return INSTANCE;
    }

    public void init(VKTerminalConfig cfg) {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            applyConfig(cfg);
            initialized = true;
        }
    }

    public void reinit(VKTerminalConfig cfg) {
        synchronized (LOCK) {
            applyConfig(cfg);
            initialized = true;
        }
    }

    public boolean started() {
        return initialized;
    }

    public VKTerminalConfig config() {
        return config.copy();
    }

    public VKTerminalTheme theme() {
        return theme.copy();
    }

    public void useTheme(VKTerminalTheme newTheme) {
        synchronized (LOCK) {
            if (newTheme != null) {
                this.theme = newTheme.copy();
                this.config = this.config.copy().theme(this.theme.copy());
            }
        }
    }

    public VKTerminalApp app() {
        ensureInit();
        return new VKTerminalApp(config.copy(), theme.copy());
    }

    public void close() {
        synchronized (LOCK) {
            initialized = false;
            config = new VKTerminalConfig();
            theme = VKTerminalTheme.defaults();
        }
    }

    private void ensureInit() {
        if (!initialized) {
            init(new VKTerminalConfig());
        }
    }

    private void applyConfig(VKTerminalConfig cfg) {
        this.config = cfg == null ? new VKTerminalConfig() : cfg.copy();
        this.theme = this.config.getTheme() == null ? VKTerminalTheme.defaults() : this.config.getTheme().copy();
        this.config.theme(this.theme.copy());
    }
}
