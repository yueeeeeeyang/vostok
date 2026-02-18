package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.security.VKSecurityConfig;
import yueyang.vostok.security.VKSecurityRiskLevel;
import yueyang.vostok.security.exception.VKSecurityException;
import yueyang.vostok.security.file.VKFileType;
import yueyang.vostok.security.rule.VKSecurityContext;
import yueyang.vostok.security.rule.VKSecurityFinding;
import yueyang.vostok.security.rule.VKSecurityRule;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class VostokSecurityTest {
    @AfterEach
    void tearDown() {
        Vostok.Security.close();
        Vostok.Security.clearCustomRules();
    }

    @Test
    void testSafeSqlByDefault() {
        var result = Vostok.Security.checkSql("SELECT id, user_name FROM t_user WHERE id = ?", 1L);
        assertTrue(result.isSafe());
        assertEquals(VKSecurityRiskLevel.LOW, result.getRiskLevel());
        assertTrue(result.getReasons().isEmpty());
    }

    @Test
    void testDetectInjectionPattern() {
        var result = Vostok.Security.checkSql("SELECT * FROM t_user WHERE name = '' OR 1=1 --");
        assertFalse(result.isSafe());
        assertEquals(VKSecurityRiskLevel.HIGH, result.getRiskLevel());
        assertFalse(result.getMatchedRules().isEmpty());
    }

    @Test
    void testRiskFunctionPattern() {
        var result = Vostok.Security.checkSql("SELECT pg_sleep(3)");
        assertFalse(result.isSafe());
        assertEquals(VKSecurityRiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    void testMultiStatementPolicy() {
        var blocked = Vostok.Security.checkSql("SELECT 1; DROP TABLE t_user");
        assertFalse(blocked.isSafe());

        Vostok.Security.reinit(new VKSecurityConfig().allowMultiStatement(true));
        var allowed = Vostok.Security.checkSql("SELECT 1; ");
        assertTrue(allowed.isSafe());
    }

    @Test
    void testPlaceholderMismatch() {
        var result = Vostok.Security.checkSql("SELECT * FROM t_user WHERE id = ? AND age = ?", 1);
        assertFalse(result.isSafe());
        assertTrue(result.getMatchedRules().contains("sql-placeholder-arity"));
    }

    @Test
    void testAssertUnsafeSqlThrows() {
        assertThrows(VKSecurityException.class,
                () -> Vostok.Security.assertSafeSql("SELECT * FROM t_user WHERE id = 1 OR 1=1"));
    }

    @Test
    void testCustomRule() {
        Vostok.Security.registerRule(new VKSecurityRule() {
            @Override
            public String name() {
                return "custom-forbid-user-table";
            }

            @Override
            public VKSecurityFinding apply(VKSecurityContext context) {
                if (context.getScannedSql().contains("t_user")) {
                    return new VKSecurityFinding(name(), VKSecurityRiskLevel.MEDIUM, 6,
                            "custom rule blocked t_user table");
                }
                return null;
            }
        });

        var result = Vostok.Security.checkSql("SELECT * FROM t_user WHERE id = ?", 1L);
        assertFalse(result.isSafe());
        assertTrue(result.getMatchedRules().contains("custom-forbid-user-table"));
    }

    @Test
    void testXssDetection() {
        var bad = Vostok.Security.checkXss("<script>alert('x')</script>");
        assertFalse(bad.isSafe());
        assertEquals(VKSecurityRiskLevel.HIGH, bad.getRiskLevel());

        var ok = Vostok.Security.checkXss("hello world");
        assertTrue(ok.isSafe());
    }

    @Test
    void testCommandInjectionDetection() {
        var bad = Vostok.Security.checkCommandInjection("ls; rm -rf /");
        assertFalse(bad.isSafe());

        var ok = Vostok.Security.checkCommandInjection("ls -la /tmp");
        assertTrue(ok.isSafe());
    }

    @Test
    void testPathTraversalDetection() {
        var bad = Vostok.Security.checkPathTraversal("../../etc/passwd");
        assertFalse(bad.isSafe());

        var encodedBad = Vostok.Security.checkPathTraversal("..%2f..%2fsecret.txt");
        assertFalse(encodedBad.isSafe());

        var ok = Vostok.Security.checkPathTraversal("uploads/avatar.png");
        assertTrue(ok.isSafe());
    }

    @Test
    void testSensitiveResponseCheckAndMask() {
        String payload = "{\"phone\":\"13800138000\",\"email\":\"a@b.com\"}";
        var check = Vostok.Security.checkSensitiveResponse(payload);
        assertFalse(check.isSafe());

        String masked = Vostok.Security.maskSensitiveResponse(payload);
        assertTrue(masked.contains("138****8000"));
        assertTrue(masked.contains("***@***"));
    }

    @Test
    void testFileMagicDetection() {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x00};
        assertEquals(VKFileType.PNG, Vostok.Security.detectFileType(png));

        var allowed = Vostok.Security.checkFileMagic(png, VKFileType.PNG, VKFileType.JPEG);
        assertTrue(allowed.isSafe());

        var blocked = Vostok.Security.checkFileMagic(png, VKFileType.PDF);
        assertFalse(blocked.isSafe());
    }

    @Test
    void testExecutableScriptUploadDetection() {
        byte[] sh = "#!/bin/bash\necho hi".getBytes(StandardCharsets.UTF_8);
        var badByExt = Vostok.Security.checkExecutableScriptUpload("run.sh", "echo hi".getBytes(StandardCharsets.UTF_8));
        assertFalse(badByExt.isSafe());

        var badByMagic = Vostok.Security.checkExecutableScriptUpload("note.txt", sh);
        assertFalse(badByMagic.isSafe());

        var ok = Vostok.Security.checkExecutableScriptUpload("photo.png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        assertTrue(ok.isSafe());
    }
}
