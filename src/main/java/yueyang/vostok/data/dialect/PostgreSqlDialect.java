package yueyang.vostok.data.dialect;

import yueyang.vostok.util.VKAssert;

/**
 * PostgreSQL 方言。
 */
public class PostgreSqlDialect implements VKDialect {
    @Override
    
    public void appendLimitOffset(StringBuilder sb, Integer limit, Integer offset) {
        if (limit == null && offset == null) {
            return;
        }
        if (limit != null) {
            VKAssert.isTrue(limit > 0, "Limit must be > 0");
            sb.append(" LIMIT ").append(limit);
        }
        if (offset != null) {
            VKAssert.isTrue(offset >= 0, "Offset must be >= 0");
            sb.append(" OFFSET ").append(offset);
        }
    }
}
