package yueyang.vostok.ai.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.ai.VKAiMessage;
import yueyang.vostok.security.VKSecurityCheckResult;

import java.util.List;

final class VKAiSecurityOps {
    private VKAiSecurityOps() {
    }

    static String firstSecurityRiskFromPrompt(String systemPrompt, List<VKAiMessage> messages) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            String r = firstPromptSecurityRisk(systemPrompt);
            if (r != null) {
                return "system:" + r;
            }
        }
        if (messages == null) {
            return null;
        }
        int idx = 0;
        for (VKAiMessage it : messages) {
            String content = it == null ? null : it.getContent();
            if (content == null || content.isBlank()) {
                idx++;
                continue;
            }
            String r = firstPromptSecurityRisk(content);
            if (r != null) {
                return "message[" + idx + "]:" + r;
            }
            idx++;
        }
        return null;
    }

    static String firstToolInputSecurityRisk(String value) {
        VKSecurityCheckResult xss = Vostok.Security.checkXss(value);
        if (!xss.isSafe()) {
            return "xss";
        }
        VKSecurityCheckResult cmd = Vostok.Security.checkCommandInjection(value);
        if (!cmd.isSafe()) {
            return "command-injection";
        }
        VKSecurityCheckResult path = Vostok.Security.checkPathTraversal(value);
        if (!path.isSafe()) {
            return "path-traversal";
        }
        return null;
    }

    private static String firstPromptSecurityRisk(String value) {
        VKSecurityCheckResult xss = Vostok.Security.checkXss(value);
        if (!xss.isSafe()) {
            return "xss";
        }
        VKSecurityCheckResult cmd = Vostok.Security.checkCommandInjection(value == null ? null : value.replace('\n', ' ').replace('\r', ' '));
        if (!cmd.isSafe()) {
            return "command-injection";
        }
        VKSecurityCheckResult path = Vostok.Security.checkPathTraversal(value);
        if (!path.isSafe()) {
            return "path-traversal";
        }
        return null;
    }
}
