package yueyang.vostok.security.crlf;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * CRLF 注入检测扫描器（Ext4）。
 *
 * <p>CRLF 注入（HTTP 响应头分割）：攻击者在 HTTP 响应头字段值中注入 CR ({@code \r}) 或
 * LF ({@code \n})，从而插入额外的响应头，可导致：
 * <ul>
 *   <li>XSS（注入 Set-Cookie / Location 等头部）</li>
 *   <li>HTTP 缓存投毒</li>
 *   <li>会话固定</li>
 * </ul>
 *
 * <p>检测对象：原始字符形式（{@code \r\n}）以及常见 URL 编码形式（{@code %0d%0a}），
 * 并通过循环解码覆盖双重编码绕过（{@code %250d%250a}）。
 */
public final class VKCrlfScanner {

    /** 匹配实际 CR/LF 字符 */
    private static final Pattern CRLF_RAW = Pattern.compile("[\\r\\n]");

    /** 匹配 URL 编码形式 %0d / %0a（%25 开头的形式在解码后处理） */
    private static final Pattern CRLF_ENCODED = Pattern.compile(
            "%0[da]", Pattern.CASE_INSENSITIVE);

    private VKCrlfScanner() {
    }

    /**
     * 检测 HTTP 响应头字段值中是否存在 CRLF 注入。
     * 先检测原始字符，再检测 URL 编码形式，最后循环解码后再检测，覆盖双重编码绕过。
     *
     * @param headerValue 待检测的响应头字段值（如 Location 目标地址）
     * @return 检测结果；不安全时 riskLevel 为 HIGH
     */
    public static VKSecurityCheckResult check(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return VKSecurityCheckResult.safe();
        }

        // 检测原始 CR/LF 字符（攻击者直接传入的情况）
        if (CRLF_RAW.matcher(headerValue).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected CR/LF character in header value (CRLF injection)", "crlf-raw");
        }

        // 检测 URL 编码形式 %0d/%0a
        if (CRLF_ENCODED.matcher(headerValue).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected URL-encoded CRLF in header value", "crlf-encoded");
        }

        // 循环 URL 解码（最多 3 轮），覆盖双重/三重编码如 %250d%250a → %0d%0a → \r\n
        String decoded = decodeAll(headerValue);
        if (!decoded.equals(headerValue)) {
            if (CRLF_RAW.matcher(decoded).find() || CRLF_ENCODED.matcher(decoded).find()) {
                return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                        "Detected double-encoded CRLF in header value", "crlf-double-encoded");
            }
        }

        return VKSecurityCheckResult.safe();
    }

    /**
     * 循环 URL 解码直至稳定（最多 3 轮），防止双重/多重编码绕过。
     */
    private static String decodeAll(String v) {
        String current = v;
        for (int i = 0; i < 3; i++) {
            String next = urlDecode(current);
            if (next.equals(current)) {
                break;
            }
            current = next;
        }
        return current;
    }

    private static String urlDecode(String v) {
        try {
            return URLDecoder.decode(v, StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            return v;
        }
    }
}
