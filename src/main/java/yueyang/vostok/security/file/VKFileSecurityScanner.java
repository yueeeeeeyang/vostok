package yueyang.vostok.security.file;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 文件上传安全检测工具。
 *
 * <p>Bug6 fix：在扩展名检测前先拒绝含 null byte 的文件名。
 * 攻击者可利用 {@code evil.php\0.jpg} 使 {@code endsWith(".jpg")} 为 true，
 * 从而绕过扩展名黑名单。
 */
public final class VKFileSecurityScanner {

    private VKFileSecurityScanner() {
    }

    /**
     * 通过魔数（magic bytes）检测文件类型，不依赖文件名扩展名。
     *
     * @param content 文件内容字节数组
     * @return 检测到的文件类型；无法识别时返回 {@link VKFileType#UNKNOWN}
     */
    public static VKFileType detectType(byte[] content) {
        if (content == null || content.length == 0) {
            return VKFileType.UNKNOWN;
        }
        if (startsWith(content, (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n')) {
            return VKFileType.PNG;
        }
        if (startsWith(content, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)) {
            return VKFileType.JPEG;
        }
        if (startsWith(content, 'G', 'I', 'F', '8')) {
            return VKFileType.GIF;
        }
        if (startsWith(content, '%', 'P', 'D', 'F', '-')) {
            return VKFileType.PDF;
        }
        if (startsWith(content, 'P', 'K', 0x03, 0x04)) {
            return VKFileType.ZIP;
        }
        if (startsWith(content, 0x1F, (byte) 0x8B)) {
            return VKFileType.GZIP;
        }
        if (startsWith(content, 0x7F, 'E', 'L', 'F')) {
            return VKFileType.ELF;
        }
        if (startsWith(content, 'M', 'Z')) {
            return VKFileType.PE;
        }
        if (startsWith(content, (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE)) {
            return VKFileType.JAVA_CLASS;
        }
        if (startsWith(content, (byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCE)
                || startsWith(content, (byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCF)
                || startsWith(content, (byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE)
                || startsWith(content, (byte) 0xCE, (byte) 0xFA, (byte) 0xED, (byte) 0xFE)) {
            return VKFileType.MACH_O;
        }
        if (startsWith(content, '#', '!')) {
            return VKFileType.SCRIPT;
        }
        return VKFileType.UNKNOWN;
    }

    /**
     * 检测文件魔数是否在允许列表中。
     *
     * @param content 文件内容
     * @param allowed 允许的文件类型列表
     * @return safe=true 表示类型在白名单中；safe=false 表示被拒绝
     */
    public static VKSecurityCheckResult checkMagicAllowed(byte[] content, VKFileType... allowed) {
        VKFileType detected = detectType(content);
        if (allowed == null || allowed.length == 0) {
            return VKSecurityCheckResult.safe();
        }
        for (VKFileType t : allowed) {
            if (t == detected) {
                return VKSecurityCheckResult.safe();
            }
        }
        return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 8,
                "File magic type is not allowed: " + detected, "file-magic-not-allowed");
    }

    /**
     * 检测上传的文件是否为可执行脚本。
     *
     * <p>检测维度（按优先级）：
     * <ol>
     *   <li>Bug6 fix：文件名含 null byte（{@code \0}）时立即拒绝，防止扩展名绕过</li>
     *   <li>可执行脚本扩展名黑名单（.sh .bat .cmd .ps1 .php .jsp .js .py .rb）</li>
     *   <li>可执行文件魔数（ELF、PE、Mach-O、Java Class、SCRIPT）</li>
     *   <li>文件头内容检测（前 512 字节）：{@code <%}、{@code <?php}、{@code #!/bin/} 等</li>
     * </ol>
     *
     * @param fileName 原始文件名（含扩展名）
     * @param content  文件内容字节数组
     * @return 检测结果
     */
    public static VKSecurityCheckResult checkExecutableScriptUpload(String fileName, byte[] content) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);

        // Bug6 fix: 先检测文件名中的 null byte，防止 evil.php\0.jpg 绕过扩展名检测
        if (lower.indexOf('\0') >= 0) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected null byte in file name, possible extension bypass attack", "upload-nullbyte-filename");
        }

        if (lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".cmd")
                || lower.endsWith(".ps1") || lower.endsWith(".php") || lower.endsWith(".jsp")
                || lower.endsWith(".js") || lower.endsWith(".py") || lower.endsWith(".rb")) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected executable script extension", "upload-script-extension");
        }

        VKFileType t = detectType(content);
        if (t == VKFileType.SCRIPT || t == VKFileType.ELF || t == VKFileType.PE
                || t == VKFileType.MACH_O || t == VKFileType.JAVA_CLASS) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected executable file content by magic", "upload-script-magic");
        }

        if (content != null && content.length > 0) {
            String head = new String(content, 0, Math.min(content.length, 512),
                    StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (head.contains("<%") || head.contains("<?php")
                    || head.contains("#!/bin/") || head.contains("powershell")) {
                return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                        "Detected executable script payload content", "upload-script-payload");
            }
        }

        return VKSecurityCheckResult.safe();
    }

    private static boolean startsWith(byte[] content, int... prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((content[i] & 0xFF) != (prefix[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }
}
