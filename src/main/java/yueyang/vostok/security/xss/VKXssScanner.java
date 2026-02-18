package yueyang.vostok.security.xss;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.util.Locale;
import java.util.regex.Pattern;

public final class VKXssScanner {
    private static final Pattern SCRIPT_TAG = Pattern.compile("<\\s*script\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_HANDLER = Pattern.compile("on[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_PROTOCOL = Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern IFRAME = Pattern.compile("<\\s*iframe\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_ONLOAD = Pattern.compile("<\\s*svg[^>]*onload\\s*=", Pattern.CASE_INSENSITIVE);

    private VKXssScanner() {
    }

    public static VKSecurityCheckResult check(String input) {
        if (input == null || input.isBlank()) {
            return VKSecurityCheckResult.safe();
        }

        String s = input.toLowerCase(Locale.ROOT);
        if (SCRIPT_TAG.matcher(s).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected script tag payload", "xss-script-tag");
        }
        if (SVG_ONLOAD.matcher(s).find() || EVENT_HANDLER.matcher(s).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 8,
                    "Detected event handler payload", "xss-event-handler");
        }
        if (JS_PROTOCOL.matcher(s).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 8,
                    "Detected javascript protocol payload", "xss-js-protocol");
        }
        if (IFRAME.matcher(s).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.MEDIUM, 6,
                    "Detected iframe payload", "xss-iframe");
        }
        return VKSecurityCheckResult.safe();
    }
}
