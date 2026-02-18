package yueyang.vostok.security.file;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class VKFileSecurityScanner {
    private VKFileSecurityScanner() {
    }

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

    public static VKSecurityCheckResult checkExecutableScriptUpload(String fileName, byte[] content) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".cmd")
                || lower.endsWith(".ps1") || lower.endsWith(".php") || lower.endsWith(".jsp")
                || lower.endsWith(".js") || lower.endsWith(".py") || lower.endsWith(".rb")) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected executable script extension", "upload-script-extension");
        }

        VKFileType t = detectType(content);
        if (t == VKFileType.SCRIPT || t == VKFileType.ELF || t == VKFileType.PE || t == VKFileType.MACH_O || t == VKFileType.JAVA_CLASS) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected executable file content by magic", "upload-script-magic");
        }

        if (content != null && content.length > 0) {
            String head = new String(content, 0, Math.min(content.length, 512), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (head.contains("<%") || head.contains("<?php") || head.contains("#!/bin/") || head.contains("powershell")) {
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
