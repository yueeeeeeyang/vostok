package yueyang.vostok.security.rule.sql;

import yueyang.vostok.security.VKSecurityRiskLevel;
import yueyang.vostok.security.rule.VKSecurityContext;
import yueyang.vostok.security.rule.VKSecurityFinding;
import yueyang.vostok.security.rule.VKSecurityRule;

public final class SqlControlCharRule implements VKSecurityRule {
    @Override
    public String name() {
        return "sql-control-char";
    }

    @Override
    public VKSecurityFinding apply(VKSecurityContext context) {
        String raw = context.getRawSql();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                return new VKSecurityFinding(name(), VKSecurityRiskLevel.MEDIUM, 5,
                        "Detected unexpected control character in SQL");
            }
        }
        return null;
    }
}
