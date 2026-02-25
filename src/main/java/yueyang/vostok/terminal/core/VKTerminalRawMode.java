package yueyang.vostok.terminal.core;

import java.io.IOException;
import java.util.Locale;

/**
 * Best-effort raw mode switch for POSIX terminals.
 */
final class VKTerminalRawMode {
    private VKTerminalRawMode() {
    }

    static RawState enable() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return RawState.disabled();
        }
        try {
            String before = exec("stty -g </dev/tty").trim();
            if (before.isEmpty()) {
                return RawState.disabled();
            }
            exec("stty -echo -icanon min 1 time 0 </dev/tty");
            return new RawState(true, before);
        } catch (Exception e) {
            return RawState.disabled();
        }
    }

    static void restore(RawState state) {
        if (state == null || !state.enabled || state.snapshot == null || state.snapshot.isBlank()) {
            return;
        }
        try {
            exec("stty " + state.snapshot + " </dev/tty");
        } catch (Exception ignore) {
            // ignore restore failures
        }
    }

    private static String exec(String command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("sh", "-lc", command).start();
        byte[] out = p.getInputStream().readAllBytes();
        byte[] err = p.getErrorStream().readAllBytes();
        int code = p.waitFor();
        if (code != 0 && err.length > 0) {
            throw new IOException(new String(err));
        }
        return new String(out);
    }

    static final class RawState {
        private final boolean enabled;
        private final String snapshot;

        RawState(boolean enabled, String snapshot) {
            this.enabled = enabled;
            this.snapshot = snapshot;
        }

        static RawState disabled() {
            return new RawState(false, null);
        }
    }
}
