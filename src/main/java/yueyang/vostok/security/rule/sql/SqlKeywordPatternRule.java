package yueyang.vostok.security.rule.sql;

import yueyang.vostok.security.VKSecurityRiskLevel;
import yueyang.vostok.security.rule.VKSecurityContext;
import yueyang.vostok.security.rule.VKSecurityFinding;
import yueyang.vostok.security.rule.VKSecurityRule;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class SqlKeywordPatternRule implements VKSecurityRule {
    private static final class RulePattern {
        private final Pattern pattern;
        private final VKSecurityRiskLevel level;
        private final int score;
        private final String reason;

        private RulePattern(String regex, VKSecurityRiskLevel level, int score, String reason) {
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.level = level;
            this.score = score;
            this.reason = reason;
        }
    }

    private final List<RulePattern> patterns;

    public SqlKeywordPatternRule(boolean strictMode) {
        this.patterns = new ArrayList<>();
        patterns.add(new RulePattern("\\bor\\s+1\\s*=\\s*1\\b", VKSecurityRiskLevel.HIGH, 9,
                "Detected tautology predicate pattern"));
        patterns.add(new RulePattern("\\bunion\\s+all?\\s+select\\b", VKSecurityRiskLevel.HIGH, 9,
                "Detected UNION SELECT injection pattern"));
        patterns.add(new RulePattern("\\bsleep\\s*\\(", VKSecurityRiskLevel.HIGH, 8,
                "Detected DB sleep function usage in SQL"));
        patterns.add(new RulePattern("\\bpg_sleep\\s*\\(", VKSecurityRiskLevel.HIGH, 8,
                "Detected pg_sleep function usage in SQL"));
        patterns.add(new RulePattern("\\bbenchmark\\s*\\(", VKSecurityRiskLevel.HIGH, 8,
                "Detected benchmark function usage in SQL"));
        patterns.add(new RulePattern("\\bload_file\\s*\\(", VKSecurityRiskLevel.HIGH, 9,
                "Detected load_file function usage in SQL"));
        patterns.add(new RulePattern("\\binto\\s+outfile\\b", VKSecurityRiskLevel.HIGH, 9,
                "Detected INTO OUTFILE data exfiltration pattern"));
        patterns.add(new RulePattern("\\bcopy\\b.+\\bprogram\\b", VKSecurityRiskLevel.HIGH, 9,
                "Detected COPY PROGRAM execution pattern"));
        patterns.add(new RulePattern("\\bxp_cmdshell\\b", VKSecurityRiskLevel.HIGH, 10,
                "Detected dangerous procedure xp_cmdshell"));
        patterns.add(new RulePattern("\\bdrop\\s+table\\b", VKSecurityRiskLevel.HIGH, 8,
                "Detected DROP TABLE statement"));
        patterns.add(new RulePattern("\\btruncate\\s+table\\b", VKSecurityRiskLevel.HIGH, 8,
                "Detected TRUNCATE TABLE statement"));

        if (strictMode) {
            patterns.add(new RulePattern("\\bor\\s+['\\\"]?[a-z0-9_]+['\\\"]?\\s*=\\s*['\\\"]?[a-z0-9_]+['\\\"]?", VKSecurityRiskLevel.MEDIUM, 6,
                    "Detected suspicious OR equality predicate under strict mode"));
        }
    }

    @Override
    public String name() {
        return "sql-keyword-pattern";
    }

    @Override
    public VKSecurityFinding apply(VKSecurityContext context) {
        String sql = context.getScannedSql();
        for (RulePattern p : patterns) {
            if (p.pattern.matcher(sql).find()) {
                return new VKSecurityFinding(name(), p.level, p.score, p.reason);
            }
        }
        return null;
    }
}
