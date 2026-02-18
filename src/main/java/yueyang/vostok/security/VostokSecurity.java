package yueyang.vostok.security;

import yueyang.vostok.security.exception.VKSecurityException;
import yueyang.vostok.security.file.VKFileSecurityScanner;
import yueyang.vostok.security.file.VKFileType;
import yueyang.vostok.security.path.VKPathTraversalScanner;
import yueyang.vostok.security.response.VKResponseSecurityScanner;
import yueyang.vostok.security.rule.VKSecurityRule;
import yueyang.vostok.security.sql.VKSqlCheckResult;
import yueyang.vostok.security.sql.VKSqlSecurityScanner;
import yueyang.vostok.security.xss.VKXssScanner;
import yueyang.vostok.security.command.VKCommandInjectionScanner;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class VostokSecurity {
    private static final Object LOCK = new Object();
    private static final List<VKSecurityRule> CUSTOM_RULES = new CopyOnWriteArrayList<>();

    private static volatile boolean initialized;
    private static volatile VKSecurityConfig config = new VKSecurityConfig();
    private static volatile VKSqlSecurityScanner scanner;

    protected VostokSecurity() {
    }

    public static void init() {
        init(new VKSecurityConfig());
    }

    public static void init(VKSecurityConfig newConfig) {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            config = (newConfig == null ? new VKSecurityConfig() : newConfig.copy());
            scanner = new VKSqlSecurityScanner(config, CUSTOM_RULES);
            initialized = true;
        }
    }

    public static void reinit(VKSecurityConfig newConfig) {
        synchronized (LOCK) {
            config = (newConfig == null ? new VKSecurityConfig() : newConfig.copy());
            scanner = new VKSqlSecurityScanner(config, CUSTOM_RULES);
            initialized = true;
        }
    }

    public static boolean started() {
        return initialized;
    }

    public static void close() {
        synchronized (LOCK) {
            scanner = null;
            initialized = false;
        }
    }

    public static VKSecurityConfig config() {
        return config.copy();
    }

    public static void registerRule(VKSecurityRule rule) {
        if (rule == null) {
            return;
        }
        CUSTOM_RULES.add(rule);
        synchronized (LOCK) {
            if (initialized) {
                scanner = new VKSqlSecurityScanner(config, CUSTOM_RULES);
            }
        }
    }

    public static void clearCustomRules() {
        CUSTOM_RULES.clear();
        synchronized (LOCK) {
            if (initialized) {
                scanner = new VKSqlSecurityScanner(config, CUSTOM_RULES);
            }
        }
    }

    public static List<String> listRules() {
        return current().ruleNames();
    }

    public static VKSqlCheckResult checkSql(String sql) {
        return current().checkSql(sql, new Object[0]);
    }

    public static VKSqlCheckResult checkSql(String sql, Object... params) {
        return current().checkSql(sql, params);
    }

    public static boolean isSafeSql(String sql) {
        return checkSql(sql).isSafe();
    }

    public static void assertSafeSql(String sql) {
        VKSqlCheckResult result = checkSql(sql);
        if (!result.isSafe()) {
            throw new VKSecurityException("Unsafe SQL detected: " + String.join("; ", result.getReasons()));
        }
    }

    public static VKSecurityCheckResult checkXss(String input) {
        return VKXssScanner.check(input);
    }

    public static void assertSafeXss(String input) {
        assertSafe("Unsafe XSS payload detected", checkXss(input));
    }

    public static VKSecurityCheckResult checkCommandInjection(String input) {
        return VKCommandInjectionScanner.check(input);
    }

    public static void assertSafeCommand(String input) {
        assertSafe("Unsafe command payload detected", checkCommandInjection(input));
    }

    public static VKSecurityCheckResult checkPathTraversal(String inputPath) {
        return VKPathTraversalScanner.check(inputPath);
    }

    public static void assertSafePath(String inputPath) {
        assertSafe("Unsafe path traversal payload detected", checkPathTraversal(inputPath));
    }

    public static VKSecurityCheckResult checkSensitiveResponse(String payload) {
        return VKResponseSecurityScanner.check(payload);
    }

    public static String maskSensitiveResponse(String payload) {
        return VKResponseSecurityScanner.mask(payload);
    }

    public static VKFileType detectFileType(byte[] content) {
        return VKFileSecurityScanner.detectType(content);
    }

    public static VKSecurityCheckResult checkFileMagic(byte[] content, VKFileType... allowed) {
        return VKFileSecurityScanner.checkMagicAllowed(content, allowed);
    }

    public static VKSecurityCheckResult checkExecutableScriptUpload(String fileName, byte[] content) {
        return VKFileSecurityScanner.checkExecutableScriptUpload(fileName, content);
    }

    private static void assertSafe(String message, VKSecurityCheckResult result) {
        if (!result.isSafe()) {
            throw new VKSecurityException(message + ": " + String.join("; ", result.getReasons()));
        }
    }

    private static VKSqlSecurityScanner current() {
        VKSqlSecurityScanner s = scanner;
        if (s != null) {
            return s;
        }
        synchronized (LOCK) {
            if (scanner == null) {
                config = config == null ? new VKSecurityConfig() : config.copy();
                scanner = new VKSqlSecurityScanner(config, CUSTOM_RULES);
                initialized = true;
            }
            return scanner;
        }
    }
}
