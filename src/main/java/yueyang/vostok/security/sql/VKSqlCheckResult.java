package yueyang.vostok.security.sql;

import yueyang.vostok.security.VKSecurityRiskLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VKSqlCheckResult {
    private final boolean safe;
    private final VKSecurityRiskLevel riskLevel;
    private final int score;
    private final String normalizedSql;
    private final List<String> reasons;
    private final List<String> matchedRules;

    public VKSqlCheckResult(boolean safe,
                            VKSecurityRiskLevel riskLevel,
                            int score,
                            String normalizedSql,
                            List<String> reasons,
                            List<String> matchedRules) {
        this.safe = safe;
        this.riskLevel = riskLevel;
        this.score = score;
        this.normalizedSql = normalizedSql;
        this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
        this.matchedRules = Collections.unmodifiableList(new ArrayList<>(matchedRules));
    }

    public static VKSqlCheckResult safe(String normalizedSql) {
        return new VKSqlCheckResult(true, VKSecurityRiskLevel.LOW, 0, normalizedSql, List.of(), List.of());
    }

    public boolean isSafe() {
        return safe;
    }

    public VKSecurityRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public int getScore() {
        return score;
    }

    public String getNormalizedSql() {
        return normalizedSql;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public List<String> getMatchedRules() {
        return matchedRules;
    }
}
