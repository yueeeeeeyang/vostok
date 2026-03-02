package yueyang.vostok.security.ssrf;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * SSRF（服务端请求伪造）检测扫描器（Ext2）。
 *
 * <p>检测 URL 参数中指向内部/私有地址的请求，防止攻击者利用服务端发起对内网的请求。
 * 检测范围：
 * <ul>
 *   <li>私有 IP 段：RFC 1918（10.x, 172.16-31.x, 192.168.x）、回环（127.x）</li>
 *   <li>云元数据地址：169.254.169.254（AWS/GCP/Azure IMDSv1）</li>
 *   <li>localhost 域名</li>
 *   <li>IPv6 回环：::1</li>
 *   <li>危险协议：file://、gopher://、dict://、ldap://、sftp:// 等</li>
 * </ul>
 */
public final class VKSsrfScanner {

    /**
     * 私有/内网 IP 段正则，匹配 IPv4 形式：
     * - 127.x.x.x（回环）
     * - 10.x.x.x（Class A 私有）
     * - 172.16-31.x.x（Class B 私有）
     * - 192.168.x.x（Class C 私有）
     * - 169.254.169.254（云实例元数据）
     * - 0.0.0.0（任意地址，通常指本机）
     */
    private static final Pattern PRIVATE_IP = Pattern.compile(
            "(?<![\\d.])" +
            "(?:" +
            "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}" +
            "|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}" +
            "|172\\.(?:1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}" +
            "|192\\.168\\.\\d{1,3}\\.\\d{1,3}" +
            "|169\\.254\\.169\\.254" +
            "|0\\.0\\.0\\.0" +
            ")" +
            "(?![\\d.])",
            Pattern.CASE_INSENSITIVE);

    /** localhost 域名，后跟端口、路径或字符串结尾 */
    private static final Pattern LOCALHOST = Pattern.compile(
            "(?:^|[/@.])localhost(?:[:/]|$)",
            Pattern.CASE_INSENSITIVE);

    /** IPv6 回环地址 ::1，含方括号形式 [::1] */
    private static final Pattern IPV6_LOOPBACK = Pattern.compile(
            "\\[?::1]?(?:[:/]|$)",
            Pattern.CASE_INSENSITIVE);

    /**
     * 危险协议：file、gopher、dict、ldap(s)、sftp、tftp、jar、netdoc。
     * 攻击者可利用这些协议读取本地文件或访问内部服务。
     */
    private static final Pattern DANGEROUS_SCHEME = Pattern.compile(
            "(?i)(?:file|gopher|dict|ldap|ldaps|sftp|tftp|jar|netdoc)://",
            Pattern.CASE_INSENSITIVE);

    private VKSsrfScanner() {
    }

    /**
     * 检测 URL 是否存在 SSRF 风险。先进行 URL 解码以防止编码绕过。
     *
     * @param url 待检测的 URL 字符串（可以是完整 URL 或单独的 host 段）
     * @return 检测结果；unsafe 时 riskLevel 为 HIGH
     */
    public static VKSecurityCheckResult check(String url) {
        if (url == null || url.isBlank()) {
            return VKSecurityCheckResult.safe();
        }

        // URL 解码，防止 %6c%6f%63%61%6c%68%6f%73%74 绕过
        String decoded = urlDecode(url);

        if (DANGEROUS_SCHEME.matcher(decoded).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected dangerous URL scheme for SSRF attack", "ssrf-dangerous-scheme");
        }
        if (PRIVATE_IP.matcher(decoded).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected private/internal IP address in URL", "ssrf-private-ip");
        }
        if (LOCALHOST.matcher(decoded).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected localhost in URL", "ssrf-localhost");
        }
        if (IPV6_LOOPBACK.matcher(decoded).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected IPv6 loopback address in URL", "ssrf-ipv6-loopback");
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
