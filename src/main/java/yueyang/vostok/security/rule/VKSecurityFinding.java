package yueyang.vostok.security.rule;

import yueyang.vostok.security.VKSecurityRiskLevel;

public final class VKSecurityFinding {
    private final String rule;
    private final VKSecurityRiskLevel level;
    private final int score;
    private final String reason;

    public VKSecurityFinding(String rule, VKSecurityRiskLevel level, int score, String reason) {
        this.rule = rule;
        this.level = level;
        this.score = score;
        this.reason = reason;
    }

    public String getRule() {
        return rule;
    }

    public VKSecurityRiskLevel getLevel() {
        return level;
    }

    public int getScore() {
        return score;
    }

    public String getReason() {
        return reason;
    }
}
