package yueyang.vostok.terminal.tool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Prompt helpers for terminal input.
 */
public final class VKPrompt {
    private VKPrompt() {
    }

    public static String readLine(String prompt, InputStream in, PrintStream out) {
        require(in, out);
        out.print(prompt == null ? "" : prompt);
        out.flush();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (true) {
                int b = in.read();
                if (b < 0 || b == '\n') {
                    break;
                }
                if (b != '\r') {
                    bytes.write(b);
                }
            }
            return bytes.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Read input failed", e);
        }
    }

    public static boolean confirm(String prompt, boolean defaultYes, InputStream in, PrintStream out) {
        String suffix = defaultYes ? " [Y/n]: " : " [y/N]: ";
        while (true) {
            String line = readLine((prompt == null ? "Confirm" : prompt) + suffix, in, out).trim().toLowerCase();
            if (line.isEmpty()) {
                return defaultYes;
            }
            if ("y".equals(line) || "yes".equals(line)) {
                return true;
            }
            if ("n".equals(line) || "no".equals(line)) {
                return false;
            }
            out.println("Please input y/n.");
        }
    }

    public static int choose(String prompt, List<String> options, int defaultIndex, InputStream in, PrintStream out) {
        require(in, out);
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("Options are empty");
        }
        int defaultIdx = Math.max(0, Math.min(options.size() - 1, defaultIndex));
        out.println(prompt == null ? "Please choose:" : prompt);
        for (int i = 0; i < options.size(); i++) {
            out.println((i + 1) + ") " + options.get(i));
        }
        String line = readLine("Select [default=" + (defaultIdx + 1) + "]: ", in, out).trim();
        if (line.isEmpty()) {
            return defaultIdx;
        }
        try {
            int value = Integer.parseInt(line);
            if (value < 1 || value > options.size()) {
                return defaultIdx;
            }
            return value - 1;
        } catch (Exception e) {
            return defaultIdx;
        }
    }

    public static List<Integer> multiChoose(String prompt, List<String> options, InputStream in, PrintStream out) {
        require(in, out);
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        out.println(prompt == null ? "Please choose multiple options:" : prompt);
        for (int i = 0; i < options.size(); i++) {
            out.println((i + 1) + ") " + options.get(i));
        }
        String line = readLine("Select indexes separated by comma: ", in, out).trim();
        if (line.isEmpty()) {
            return List.of();
        }

        List<Integer> outIndexes = new ArrayList<>();
        for (String token : line.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                int idx = Integer.parseInt(t) - 1;
                if (idx >= 0 && idx < options.size() && !outIndexes.contains(idx)) {
                    outIndexes.add(idx);
                }
            } catch (Exception ignore) {
                // ignored
            }
        }
        Collections.sort(outIndexes);
        return outIndexes;
    }

    private static void require(InputStream in, PrintStream out) {
        if (in == null) {
            throw new IllegalArgumentException("InputStream is null");
        }
        if (out == null) {
            throw new IllegalArgumentException("PrintStream is null");
        }
    }
}
