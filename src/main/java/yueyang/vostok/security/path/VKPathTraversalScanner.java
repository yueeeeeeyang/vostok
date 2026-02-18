package yueyang.vostok.security.path;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class VKPathTraversalScanner {
    private VKPathTraversalScanner() {
    }

    public static VKSecurityCheckResult check(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            return VKSecurityCheckResult.safe();
        }

        String decoded = decode(inputPath);
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

    private static String decode(String v) {
        try {
            return URLDecoder.decode(v, StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            return v;
        }
    }
}
