package yueyang.vostok.security.command;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.util.Locale;
import java.util.regex.Pattern;

public final class VKCommandInjectionScanner {
    private static final Pattern SHELL_META = Pattern.compile("[;&|`]|\\$\\(|\\n|\\r");
    private static final Pattern DANGEROUS_CMD = Pattern.compile("\\b(rm\\s+-rf|curl\\s+|wget\\s+|nc\\s+|bash\\s+-c|sh\\s+-c|powershell\\s+)\\b", Pattern.CASE_INSENSITIVE);

    private VKCommandInjectionScanner() {
    }

    public static VKSecurityCheckResult check(String input) {
        if (input == null || input.isBlank()) {
            return VKSecurityCheckResult.safe();
        }

        String s = input.toLowerCase(Locale.ROOT);
        if (DANGEROUS_CMD.matcher(s).find() && SHELL_META.matcher(s).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected dangerous command with shell metacharacters", "cmd-dangerous-combo");
        }
        if (SHELL_META.matcher(s).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.MEDIUM, 6,
                    "Detected shell metacharacter injection pattern", "cmd-shell-meta");
        }
        return VKSecurityCheckResult.safe();
    }
}
