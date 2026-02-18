package yueyang.vostok.security.response;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.util.regex.Pattern;

public final class VKResponseSecurityScanner {
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE_CN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern ID_CARD_CN = Pattern.compile("(?<!\\d)(\\d{17}[\\dXx])(?!\\d)");
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)(\\d{16,19})(?!\\d)");

    private VKResponseSecurityScanner() {
    }

    public static VKSecurityCheckResult check(String payload) {
        if (payload == null || payload.isBlank()) {
            return VKSecurityCheckResult.safe();
        }
        if (EMAIL.matcher(payload).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.MEDIUM, 6,
                    "Detected email in response payload", "resp-sensitive-email");
        }
        if (PHONE_CN.matcher(payload).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 8,
                    "Detected phone number in response payload", "resp-sensitive-phone");
        }
        if (ID_CARD_CN.matcher(payload).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 8,
                    "Detected ID card number in response payload", "resp-sensitive-idcard");
        }
        if (BANK_CARD.matcher(payload).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 8,
                    "Detected bank card number in response payload", "resp-sensitive-bankcard");
        }
        return VKSecurityCheckResult.safe();
    }

    public static String mask(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        String out = payload;
        out = EMAIL.matcher(out).replaceAll("***@***");
        out = PHONE_CN.matcher(out).replaceAll(m -> maskDigits(m.group(1), 3, 4));
        out = ID_CARD_CN.matcher(out).replaceAll(m -> maskMiddle(m.group(1), 4, 4));
        out = BANK_CARD.matcher(out).replaceAll(m -> maskMiddle(m.group(1), 6, 4));
        return out;
    }

    private static String maskDigits(String value, int keepPrefix, int keepSuffix) {
        return maskMiddle(value, keepPrefix, keepSuffix);
    }

    private static String maskMiddle(String value, int keepPrefix, int keepSuffix) {
        if (value == null) {
            return null;
        }
        if (value.length() <= keepPrefix + keepSuffix) {
            return "***";
        }
        String prefix = value.substring(0, keepPrefix);
        String suffix = value.substring(value.length() - keepSuffix);
        return prefix + "*".repeat(value.length() - keepPrefix - keepSuffix) + suffix;
    }
}
