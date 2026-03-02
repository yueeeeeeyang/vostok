package yueyang.vostok.security.path;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 路径遍历检测扫描器。
 *
 * <p>Bug4 fix：循环 URL 解码（最多 3 轮）直至稳定，覆盖双重编码绕过。
 * 原实现只解码一次，{@code %252e%252e%252f} → {@code %2e%2e%2f} 不含 {@code ../}，
 * 可绕过检测。经两次解码后变为 {@code ../}，能正确命中。
 *
 * <p>检测覆盖：
 * <ul>
 *   <li>{@code ../}（及反斜杠形式，统一转换为 {@code /}）</li>
 *   <li>{@code ..%2f}（在某些框架中解码不完整时的残留形式）</li>
 *   <li>null byte（{@code %00} / {@code \0}），用于截断路径扩展名检测</li>
 * </ul>
 */
public final class VKPathTraversalScanner {

    private VKPathTraversalScanner() {
    }

    /**
     * 检测路径输入是否存在目录遍历风险。
     *
     * @param inputPath 待检测的路径字符串（可以是原始用户输入，含 URL 编码）
     * @return 检测结果
     */
    public static VKSecurityCheckResult check(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            return VKSecurityCheckResult.safe();
        }

        // Bug4 fix: 循环解码直至稳定，防止双重/三重编码（如 %252e%252e%252f）绕过
        String decoded = decodeAll(inputPath);
        // 统一将反斜杠替换为斜杠（处理 ..\\ Windows 路径），再小写化
        String normalized = decoded.replace('\\', '/').toLowerCase();

        if (normalized.contains("../") || normalized.contains("..%2f")) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected directory traversal pattern", "path-traversal-dotdot");
        }
        if (normalized.contains("%00") || normalized.indexOf('\0') >= 0) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 8,
                    "Detected null-byte traversal bypass pattern", "path-traversal-null-byte");
        }
        return VKSecurityCheckResult.safe();
    }

    /**
     * 循环 URL 解码直至内容稳定（最多 3 轮）。
     * 防止 {@code %252e%252e%252f}（%25 = % 的编码）之类的多重编码绕过。
     *
     * <p>示例：
     * <pre>
     *   %252e%252e%252f  → (decode1) → %2e%2e%2f
     *                    → (decode2) → ../   ← 命中检测
     * </pre>
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
