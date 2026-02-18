package yueyang.vostok.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VKSecurityCheckResult {
    private final boolean safe;
    private final VKSecurityRiskLevel riskLevel;
    private final int score;
    private final List<String> reasons;
    private final List<String> matchedRules;

    public VKSecurityCheckResult(boolean safe,
                                 VKSecurityRiskLevel riskLevel,
                                 int score,
                                 List<String> reasons,
                                 List<String> matchedRules) {
        this.safe = safe;
        this.riskLevel = riskLevel;
        this.score = score;
        this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
        this.matchedRules = Collections.unmodifiableList(new ArrayList<>(matchedRules));
    }

    public static VKSecurityCheckResult safe() {
        return new VKSecurityCheckResult(true, VKSecurityRiskLevel.LOW, 0, List.of(), List.of());
    }

    public static VKSecurityCheckResult unsafe(VKSecurityRiskLevel level, int score, String reason, String rule) {
        return new VKSecurityCheckResult(false, level, score, List.of(reason), List.of(rule));
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

    public List<String> getReasons() {
        return reasons;
    }

    public List<String> getMatchedRules() {
        return matchedRules;
    }
}
