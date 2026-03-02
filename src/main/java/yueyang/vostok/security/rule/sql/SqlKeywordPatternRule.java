package yueyang.vostok.security.rule.sql;

import yueyang.vostok.security.VKSecurityRiskLevel;
import yueyang.vostok.security.rule.VKSecurityContext;
import yueyang.vostok.security.rule.VKSecurityFinding;
import yueyang.vostok.security.rule.VKSecurityRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SQL 注入关键字模式检测规则。
 *
 * <p>Bug7 fix：遍历所有模式，返回得分最高的命中，不在首个命中时立即返回。
 * 原实现在首次命中时即 return，SQL 同时含 OR 1=1(score=9) 与 xp_cmdshell(score=10) 时，
 * xp_cmdshell 的更高风险得分被忽略。
 *
 * <p>Perf5 fix：与 strictMode 无关的基础模式声明为 {@code static final}，
 * 避免每次 {@code new SqlKeywordPatternRule()} 时重复编译 11 条 Pattern。
 */
public final class SqlKeywordPatternRule implements VKSecurityRule {

    private static final class RulePattern {
        final Pattern pattern;
        final VKSecurityRiskLevel level;
        final int score;
        final String reason;

        private RulePattern(String regex, VKSecurityRiskLevel level, int score, String reason) {
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.level = level;
            this.score = score;
            this.reason = reason;
        }
    }

    /**
     * Perf5 fix：基础规则声明为 static final，在类加载时编译一次，
     * 后续所有 SqlKeywordPatternRule 实例（含 reinit/registerRule 触发的重建）共享同一份 Pattern。
     */
    private static final List<RulePattern> BASE_PATTERNS = buildBasePatterns();

    private static List<RulePattern> buildBasePatterns() {
        List<RulePattern> list = new ArrayList<>();
        list.add(new RulePattern("\\bor\\s+1\\s*=\\s*1\\b",
                VKSecurityRiskLevel.HIGH, 9, "Detected tautology predicate pattern"));
        list.add(new RulePattern("\\bunion\\s+all?\\s+select\\b",
                VKSecurityRiskLevel.HIGH, 9, "Detected UNION SELECT injection pattern"));
        list.add(new RulePattern("\\bsleep\\s*\\(",
                VKSecurityRiskLevel.HIGH, 8, "Detected DB sleep function usage in SQL"));
        list.add(new RulePattern("\\bpg_sleep\\s*\\(",
                VKSecurityRiskLevel.HIGH, 8, "Detected pg_sleep function usage in SQL"));
        list.add(new RulePattern("\\bbenchmark\\s*\\(",
                VKSecurityRiskLevel.HIGH, 8, "Detected benchmark function usage in SQL"));
        list.add(new RulePattern("\\bload_file\\s*\\(",
                VKSecurityRiskLevel.HIGH, 9, "Detected load_file function usage in SQL"));
        list.add(new RulePattern("\\binto\\s+outfile\\b",
                VKSecurityRiskLevel.HIGH, 9, "Detected INTO OUTFILE data exfiltration pattern"));
        list.add(new RulePattern("\\bcopy\\b.+\\bprogram\\b",
                VKSecurityRiskLevel.HIGH, 9, "Detected COPY PROGRAM execution pattern"));
        list.add(new RulePattern("\\bxp_cmdshell\\b",
                VKSecurityRiskLevel.HIGH, 10, "Detected dangerous procedure xp_cmdshell"));
        list.add(new RulePattern("\\bdrop\\s+table\\b",
                VKSecurityRiskLevel.HIGH, 8, "Detected DROP TABLE statement"));
        list.add(new RulePattern("\\btruncate\\s+table\\b",
                VKSecurityRiskLevel.HIGH, 8, "Detected TRUNCATE TABLE statement"));
        return Collections.unmodifiableList(list);
    }

    /** 运行时使用的模式列表：非严格模式 = BASE_PATTERNS；严格模式 = BASE_PATTERNS + 额外规则 */
    private final List<RulePattern> patterns;

    public SqlKeywordPatternRule(boolean strictMode) {
        if (strictMode) {
            // 严格模式追加可疑 OR 等值判断规则，其他模式复用静态常量
            List<RulePattern> all = new ArrayList<>(BASE_PATTERNS);
            all.add(new RulePattern(
                    "\\bor\\s+['\"]?[a-z0-9_]+['\"]?\\s*=\\s*['\"]?[a-z0-9_]+['\"]?",
                    VKSecurityRiskLevel.MEDIUM, 6,
                    "Detected suspicious OR equality predicate under strict mode"));
            this.patterns = Collections.unmodifiableList(all);
        } else {
            // Perf5 fix：直接使用静态列表，不重新 new
            this.patterns = BASE_PATTERNS;
        }
    }

    @Override
    public String name() {
        return "sql-keyword-pattern";
    }

    /**
     * Bug7 fix：遍历全部模式，取 score 最高的命中结果返回。
     * 原实现首次命中即 return，导致更高分模式（如 xp_cmdshell score=10）被跳过。
     */
    @Override
    public VKSecurityFinding apply(VKSecurityContext context) {
        String sql = context.getScannedSql();
        VKSecurityFinding best = null;
        for (RulePattern p : patterns) {
            if (p.pattern.matcher(sql).find()) {
                // 取 score 最高的命中；score 相同时保留先找到的（通常风险更典型）
                if (best == null || p.score > best.getScore()) {
                    best = new VKSecurityFinding(name(), p.level, p.score, p.reason);
                }
            }
        }
        return best;
    }
}
