package yueyang.vostok.security.rule.sql;

import yueyang.vostok.security.VKSecurityRiskLevel;
import yueyang.vostok.security.rule.VKSecurityContext;
import yueyang.vostok.security.rule.VKSecurityFinding;
import yueyang.vostok.security.rule.VKSecurityRule;

public final class SqlCommentTokenRule implements VKSecurityRule {
    @Override
    public String name() {
        return "sql-comment-token";
    }

    @Override
    public VKSecurityFinding apply(VKSecurityContext context) {
        if (context.getConfig().isAllowCommentToken()) {
            return null;
        }
        String sql = context.getScannedSql();
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/") || sql.contains("#")) {
            return new VKSecurityFinding(name(), VKSecurityRiskLevel.HIGH, 8,
                    "Detected SQL comment token, possible truncation or bypass payload");
        }
        return null;
    }
}
