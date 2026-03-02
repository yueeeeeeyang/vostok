package yueyang.vostok.security.xss;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * XSS（跨站脚本）检测扫描器。
 *
 * <p>Bug1 fix：检测前先进行 URL 解码，防止 {@code %3Cscript%3E} 绕过。
 * <p>Bug2 fix：EVENT_HANDLER 模式新增 HTML 标签上下文约束（需在 {@code <tag ...>} 内），
 * 避免 {@code online=true}、{@code content=...} 等合法查询参数误报。
 * <p>Perf2 fix：去掉冗余的 {@code toLowerCase()}，所有 Pattern 均已设置 CASE_INSENSITIVE。
 */
public final class VKXssScanner {

    private static final Pattern SCRIPT_TAG =
            Pattern.compile("<\\s*script\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Bug2 fix：要求 onXXX 属性出现在 HTML 标签内部（{@code <tag ... onXXX=}），
     * 且其前驱字符为空白、引号或等号，排除 {@code data-online}、{@code ongoing} 等误报。
     * {@code [^>]{0,500}} 限制最大回溯深度，防止 ReDoS。
     */
    private static final Pattern EVENT_HANDLER =
            Pattern.compile("<[^>]{0,500}[\\s'\"=]on[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);

    private static final Pattern JS_PROTOCOL =
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE);

    private static final Pattern IFRAME =
            Pattern.compile("<\\s*iframe\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern SVG_ONLOAD =
            Pattern.compile("<\\s*svg[^>]*onload\\s*=", Pattern.CASE_INSENSITIVE);

    private VKXssScanner() {
    }

    /**
     * 检测输入字符串中是否存在 XSS 载荷。
     *
     * <p>检测流程：
     * <ol>
     *   <li>URL 解码（循环两次覆盖双重编码），防止 {@code %3Cscript%3E} 绕过</li>
     *   <li>匹配 script 标签、事件处理属性、javascript 协议、iframe 标签</li>
     * </ol>
     *
     * @param input 待检测的用户输入
     * @return 检测结果
     */
    public static VKSecurityCheckResult check(String input) {
        if (input == null || input.isBlank()) {
            return VKSecurityCheckResult.safe();
        }

        // Bug1 fix: URL 解码（最多两次），防止 %3Cscript%3E / %253Cscript%253E 绕过
        // Perf2 fix: 直接对解码后的原始大小写字符串匹配，所有 Pattern 均已含 CASE_INSENSITIVE
        String decoded = urlDecode(urlDecode(input));

        if (SCRIPT_TAG.matcher(decoded).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected script tag payload", "xss-script-tag");
        }
        if (SVG_ONLOAD.matcher(decoded).find() || EVENT_HANDLER.matcher(decoded).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 8,
                    "Detected event handler payload", "xss-event-handler");
        }
        if (JS_PROTOCOL.matcher(decoded).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 8,
                    "Detected javascript protocol payload", "xss-js-protocol");
        }
        if (IFRAME.matcher(decoded).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.MEDIUM, 6,
                    "Detected iframe payload", "xss-iframe");
        }
        return VKSecurityCheckResult.safe();
    }

    private static String urlDecode(String v) {
        try {
            return URLDecoder.decode(v, StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            return v;
        }
    }
}
