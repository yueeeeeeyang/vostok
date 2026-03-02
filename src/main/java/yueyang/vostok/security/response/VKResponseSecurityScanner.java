package yueyang.vostok.security.response;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * 响应体敏感数据检测与脱敏工具。
 *
 * <p>Bug5 fix：去掉 early-return 模式，改为遍历全部规则收集所有命中，
 * 返回最高风险等级。原实现遇到 email（MEDIUM）即返回，会漏报同时存在的银行卡（HIGH）。
 *
 * <p>Ext6：支持注册自定义敏感字段正则（{@link #addSensitivePattern(String)}），
 * 业务方无需修改框架代码即可扩展敏感字段类型。
 */
public final class VKResponseSecurityScanner {

    private static final Pattern EMAIL =
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private static final Pattern PHONE_CN =
            Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");

    private static final Pattern ID_CARD_CN =
            Pattern.compile("(?<!\\d)(\\d{17}[\\dXx])(?!\\d)");

    private static final Pattern BANK_CARD =
            Pattern.compile("(?<!\\d)(\\d{16,19})(?!\\d)");

    /**
     * Ext6：自定义敏感字段模式列表，线程安全。
     * 通过 {@link #addSensitivePattern(String)} 注册，{@link #clearSensitivePatterns()} 清空。
     */
    private static final CopyOnWriteArrayList<Pattern> CUSTOM_PATTERNS = new CopyOnWriteArrayList<>();

    private VKResponseSecurityScanner() {
    }

    /**
     * Ext6：注册自定义敏感字段正则。命中时风险等级为 MEDIUM/score=6。
     *
     * @param regex 正则表达式，编译失败时静默忽略
     */
    public static void addSensitivePattern(String regex) {
        if (regex == null || regex.isBlank()) {
            return;
        }
        try {
            CUSTOM_PATTERNS.add(Pattern.compile(regex));
        } catch (Exception ignore) {
            // 非法正则静默忽略，不影响已有功能
        }
    }

    /** Ext6：清除所有自定义敏感字段正则 */
    public static void clearSensitivePatterns() {
        CUSTOM_PATTERNS.clear();
    }

    /**
     * 检测响应体中是否存在敏感数据泄露。
     *
     * <p>Bug5 fix：遍历全部规则，收集所有命中，返回最高风险。
     * 以前的实现遇到 email 就 early-return，可能漏报更高风险的银行卡/身份证。
     *
     * @param payload 响应体字符串
     * @return 检测结果；若存在多种敏感数据，返回最高风险等级，并列出所有命中原因
     */
    public static VKSecurityCheckResult check(String payload) {
        if (payload == null || payload.isBlank()) {
            return VKSecurityCheckResult.safe();
        }

        List<String> reasons = new ArrayList<>();
        List<String> rules = new ArrayList<>();
        int totalScore = 0;
        VKSecurityRiskLevel maxLevel = VKSecurityRiskLevel.LOW;

        // Bug5 fix: 不再 early-return，遍历所有规则
        if (EMAIL.matcher(payload).find()) {
            reasons.add("Detected email in response payload");
            rules.add("resp-sensitive-email");
            totalScore += 6;
            maxLevel = higher(maxLevel, VKSecurityRiskLevel.MEDIUM);
        }
        if (PHONE_CN.matcher(payload).find()) {
            reasons.add("Detected phone number in response payload");
            rules.add("resp-sensitive-phone");
            totalScore += 8;
            maxLevel = higher(maxLevel, VKSecurityRiskLevel.HIGH);
        }
        if (ID_CARD_CN.matcher(payload).find()) {
            reasons.add("Detected ID card number in response payload");
            rules.add("resp-sensitive-idcard");
            totalScore += 8;
            maxLevel = higher(maxLevel, VKSecurityRiskLevel.HIGH);
        }
        if (BANK_CARD.matcher(payload).find()) {
            reasons.add("Detected bank card number in response payload");
            rules.add("resp-sensitive-bankcard");
            totalScore += 8;
            maxLevel = higher(maxLevel, VKSecurityRiskLevel.HIGH);
        }

        // Ext6: 自定义敏感字段模式
        for (Pattern custom : CUSTOM_PATTERNS) {
            if (custom.matcher(payload).find()) {
                reasons.add("Detected custom sensitive pattern: " + custom.pattern());
                rules.add("resp-sensitive-custom");
                totalScore += 6;
                maxLevel = higher(maxLevel, VKSecurityRiskLevel.MEDIUM);
            }
        }

        if (reasons.isEmpty()) {
            return VKSecurityCheckResult.safe();
        }
        return new VKSecurityCheckResult(false, maxLevel, totalScore, reasons, rules);
    }

    /**
     * 对响应体中的敏感数据进行脱敏处理，返回脱敏后的字符串。
     *
     * @param payload 原始响应体
     * @return 脱敏后的字符串；null 返回 null
     */
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

    /** 取两个风险等级中较高者 */
    private static VKSecurityRiskLevel higher(VKSecurityRiskLevel a, VKSecurityRiskLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
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
