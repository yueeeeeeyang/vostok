package yueyang.vostok.security.sql;

import yueyang.vostok.security.VKSecurityConfig;
import yueyang.vostok.security.VKSecurityRiskLevel;
import yueyang.vostok.security.exception.VKSecurityException;
import yueyang.vostok.security.rule.VKSecurityContext;
import yueyang.vostok.security.rule.VKSecurityFinding;
import yueyang.vostok.security.rule.VKSecurityRule;
import yueyang.vostok.security.rule.sql.SqlCommentTokenRule;
import yueyang.vostok.security.rule.sql.SqlControlCharRule;
import yueyang.vostok.security.rule.sql.SqlKeywordPatternRule;
import yueyang.vostok.security.rule.sql.SqlLengthRule;
import yueyang.vostok.security.rule.sql.SqlMultiStatementRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class VKSqlSecurityScanner {
    private final VKSecurityConfig config;
    private final List<VKSecurityRule> rules;
    private final List<Pattern> whitelistPatterns;
    private final List<Pattern> blacklistPatterns;

    public VKSqlSecurityScanner(VKSecurityConfig config, List<VKSecurityRule> customRules) {
        this.config = config == null ? new VKSecurityConfig() : config.copy();
        this.rules = new ArrayList<>();
        if (this.config.isBuiltinRulesEnabled()) {
            this.rules.add(new SqlLengthRule());
            this.rules.add(new SqlControlCharRule());
            this.rules.add(new SqlCommentTokenRule());
            this.rules.add(new SqlMultiStatementRule());
            this.rules.add(new SqlKeywordPatternRule(this.config.isStrictMode()));
        }
        if (customRules != null && !customRules.isEmpty()) {
            this.rules.addAll(customRules);
        }

        this.whitelistPatterns = compilePatterns(this.config.getWhitelistPatterns());
        this.blacklistPatterns = compilePatterns(this.config.getBlacklistPatterns());
    }

    public List<String> ruleNames() {
        ArrayList<String> out = new ArrayList<>();
        for (VKSecurityRule r : rules) {
            out.add(r.name());
        }
        return List.copyOf(out);
    }

    public VKSqlCheckResult checkSql(String sql, Object... params) {
        if (!config.isEnabled()) {
            return VKSqlCheckResult.safe(normalizeSql(sql));
        }

        String normalized = normalizeSql(sql);
        if (normalized.isEmpty()) {
            if (config.isFailOnInvalidInput()) {
                throw new VKSecurityException("SQL is blank");
            }
            return unsafe(normalized,
                    List.of("SQL is blank"),
                    List.of("sql-invalid-input"),
                    10,
                    VKSecurityRiskLevel.HIGH);
        }

        String scanned = maskSqlLiterals(normalized).toLowerCase(Locale.ROOT);

        for (Pattern p : whitelistPatterns) {
            if (p.matcher(normalized).find()) {
                return VKSqlCheckResult.safe(normalized);
            }
        }

        ArrayList<VKSecurityFinding> findings = new ArrayList<>();
        for (Pattern p : blacklistPatterns) {
            if (p.matcher(normalized).find()) {
                findings.add(new VKSecurityFinding("sql-blacklist-pattern", VKSecurityRiskLevel.HIGH, 10,
                        "Matched custom blacklist pattern: " + p.pattern()));
            }
        }

        VKSecurityContext context = new VKSecurityContext(sql, normalized, scanned, params, config);
        for (VKSecurityRule rule : rules) {
            VKSecurityFinding finding = rule.apply(context);
            if (finding != null) {
                findings.add(finding);
            }
        }

        VKSecurityFinding placeholderFinding = checkPlaceholder(normalized, params);
        if (placeholderFinding != null) {
            findings.add(placeholderFinding);
        }

        if (findings.isEmpty()) {
            return VKSqlCheckResult.safe(normalized);
        }

        int score = 0;
        VKSecurityRiskLevel max = VKSecurityRiskLevel.LOW;
        ArrayList<String> reasons = new ArrayList<>();
        ArrayList<String> matched = new ArrayList<>();
        for (VKSecurityFinding finding : findings) {
            score += finding.getScore();
            reasons.add(finding.getReason());
            matched.add(finding.getRule());
            if (finding.getLevel().ordinal() > max.ordinal()) {
                max = finding.getLevel();
            }
        }

        VKSecurityRiskLevel scoredLevel = riskByScore(score);
        VKSecurityRiskLevel finalLevel = max.ordinal() > scoredLevel.ordinal() ? max : scoredLevel;
        boolean safe = finalLevel.ordinal() < config.getRiskThreshold().ordinal();

        return new VKSqlCheckResult(safe, finalLevel, score, normalized, reasons, matched);
    }

    private VKSecurityFinding checkPlaceholder(String sql, Object[] params) {
        int paramCount = params == null ? 0 : params.length;
        int placeholders = 0;
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (c == '?' && !inSingle && !inDouble) {
                placeholders++;
            }
        }

        if (placeholders != paramCount) {
            return new VKSecurityFinding("sql-placeholder-arity", VKSecurityRiskLevel.HIGH, 8,
                    "SQL placeholder count does not match parameter count");
        }
        return null;
    }

    private static List<Pattern> compilePatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return List.of();
        }
        ArrayList<Pattern> out = new ArrayList<>();
        for (String p : patterns) {
            try {
                out.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE));
            } catch (Exception ignore) {
            }
        }
        return List.copyOf(out);
    }

    private static VKSqlCheckResult unsafe(String normalized,
                                           List<String> reasons,
                                           List<String> matched,
                                           int score,
                                           VKSecurityRiskLevel level) {
        return new VKSqlCheckResult(false, level, score, normalized, reasons, matched);
    }

    private static VKSecurityRiskLevel riskByScore(int score) {
        if (score >= 8) {
            return VKSecurityRiskLevel.HIGH;
        }
        if (score >= 4) {
            return VKSecurityRiskLevel.MEDIUM;
        }
        return VKSecurityRiskLevel.LOW;
    }

    private static String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.trim().replaceAll("\\s+", " ");
    }

    private static String maskSqlLiterals(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            if (c == '\'' && !inDouble) {
                if (inSingle && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    out.append(' ');
                    out.append(' ');
                    i++;
                    continue;
                }
                inSingle = !inSingle;
                out.append(' ');
                continue;
            }

            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                out.append(' ');
                continue;
            }

            if (inSingle || inDouble) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }

        return out.toString();
    }
}
