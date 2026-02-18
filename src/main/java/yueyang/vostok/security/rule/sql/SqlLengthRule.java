package yueyang.vostok.security.rule.sql;

import yueyang.vostok.security.VKSecurityRiskLevel;
import yueyang.vostok.security.rule.VKSecurityContext;
import yueyang.vostok.security.rule.VKSecurityFinding;
import yueyang.vostok.security.rule.VKSecurityRule;

public final class SqlLengthRule implements VKSecurityRule {
    @Override
    public String name() {
        return "sql-length";
    }

    @Override
    public VKSecurityFinding apply(VKSecurityContext context) {
        int max = context.getConfig().getMaxSqlLength();
        int len = context.getRawSql().length();
        if (len > max) {
            return new VKSecurityFinding(name(), VKSecurityRiskLevel.MEDIUM, 5,
                    "SQL length exceeds configured max length: " + max);
        }
        return null;
    }
}
