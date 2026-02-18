package yueyang.vostok.security;

import java.util.ArrayList;
import java.util.List;

public class VKSecurityConfig {
    private boolean enabled = true;
    private boolean strictMode = false;
    private boolean allowMultiStatement = false;
    private boolean allowCommentToken = false;
    private int maxSqlLength = 10_000;
    private VKSecurityRiskLevel riskThreshold = VKSecurityRiskLevel.MEDIUM;
    private boolean builtinRulesEnabled = true;
    private List<String> whitelistPatterns = new ArrayList<>();
    private List<String> blacklistPatterns = new ArrayList<>();
    private boolean failOnInvalidInput = true;

    public boolean isEnabled() {
        return enabled;
    }

    public VKSecurityConfig enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public VKSecurityConfig strictMode(boolean strictMode) {
        this.strictMode = strictMode;
        return this;
    }

    public boolean isAllowMultiStatement() {
        return allowMultiStatement;
    }

    public VKSecurityConfig allowMultiStatement(boolean allowMultiStatement) {
        this.allowMultiStatement = allowMultiStatement;
        return this;
    }

    public boolean isAllowCommentToken() {
        return allowCommentToken;
    }

    public VKSecurityConfig allowCommentToken(boolean allowCommentToken) {
        this.allowCommentToken = allowCommentToken;
        return this;
    }

    public int getMaxSqlLength() {
        return maxSqlLength;
    }

    public VKSecurityConfig maxSqlLength(int maxSqlLength) {
        this.maxSqlLength = Math.max(128, maxSqlLength);
        return this;
    }

    public VKSecurityRiskLevel getRiskThreshold() {
        return riskThreshold;
    }

    public VKSecurityConfig riskThreshold(VKSecurityRiskLevel riskThreshold) {
        this.riskThreshold = riskThreshold == null ? VKSecurityRiskLevel.MEDIUM : riskThreshold;
        return this;
    }

    public boolean isBuiltinRulesEnabled() {
        return builtinRulesEnabled;
    }

    public VKSecurityConfig builtinRulesEnabled(boolean builtinRulesEnabled) {
        this.builtinRulesEnabled = builtinRulesEnabled;
        return this;
    }

    public List<String> getWhitelistPatterns() {
        return List.copyOf(whitelistPatterns);
    }

    public VKSecurityConfig whitelistPatterns(List<String> whitelistPatterns) {
        this.whitelistPatterns = sanitizePatterns(whitelistPatterns);
        return this;
    }

    public VKSecurityConfig whitelistPatterns(String... whitelistPatterns) {
        this.whitelistPatterns = sanitizePatterns(whitelistPatterns == null ? List.of() : List.of(whitelistPatterns));
        return this;
    }

    public List<String> getBlacklistPatterns() {
        return List.copyOf(blacklistPatterns);
    }

    public VKSecurityConfig blacklistPatterns(List<String> blacklistPatterns) {
        this.blacklistPatterns = sanitizePatterns(blacklistPatterns);
        return this;
    }

    public VKSecurityConfig blacklistPatterns(String... blacklistPatterns) {
        this.blacklistPatterns = sanitizePatterns(blacklistPatterns == null ? List.of() : List.of(blacklistPatterns));
        return this;
    }

    public boolean isFailOnInvalidInput() {
        return failOnInvalidInput;
    }

    public VKSecurityConfig failOnInvalidInput(boolean failOnInvalidInput) {
        this.failOnInvalidInput = failOnInvalidInput;
        return this;
    }

    public VKSecurityConfig copy() {
        VKSecurityConfig c = new VKSecurityConfig();
        c.enabled = this.enabled;
        c.strictMode = this.strictMode;
        c.allowMultiStatement = this.allowMultiStatement;
        c.allowCommentToken = this.allowCommentToken;
        c.maxSqlLength = this.maxSqlLength;
        c.riskThreshold = this.riskThreshold;
        c.builtinRulesEnabled = this.builtinRulesEnabled;
        c.whitelistPatterns = new ArrayList<>(this.whitelistPatterns);
        c.blacklistPatterns = new ArrayList<>(this.blacklistPatterns);
        c.failOnInvalidInput = this.failOnInvalidInput;
        return c;
    }

    private static List<String> sanitizePatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return new ArrayList<>();
        }
        ArrayList<String> out = new ArrayList<>();
        for (String p : patterns) {
            if (p != null && !p.isBlank()) {
                out.add(p.trim());
            }
        }
        return out;
    }
}
