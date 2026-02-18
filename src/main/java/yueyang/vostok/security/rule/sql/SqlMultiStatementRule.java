package yueyang.vostok.security.rule.sql;

import yueyang.vostok.security.VKSecurityRiskLevel;
import yueyang.vostok.security.rule.VKSecurityContext;
import yueyang.vostok.security.rule.VKSecurityFinding;
import yueyang.vostok.security.rule.VKSecurityRule;

public final class SqlMultiStatementRule implements VKSecurityRule {
    @Override
    public String name() {
        return "sql-multi-statement";
    }

    @Override
    public VKSecurityFinding apply(VKSecurityContext context) {
        if (context.getConfig().isAllowMultiStatement()) {
            return null;
        }
        String sql = context.getScannedSql();
        int pos = sql.indexOf(';');
        if (pos >= 0 && pos < sql.length() - 1) {
            return new VKSecurityFinding(name(), VKSecurityRiskLevel.HIGH, 8,
                    "Detected multi statement separator ';'");
        }
        return null;
    }
}
