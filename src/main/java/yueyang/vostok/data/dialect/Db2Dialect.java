package yueyang.vostok.data.dialect;

import yueyang.vostok.util.VKAssert;

/**
 * DB2 方言（OFFSET ... ROWS / FETCH FIRST ... ROWS ONLY）。
 */
public class Db2Dialect implements VKDialect {
    @Override
    
    public void appendLimitOffset(StringBuilder sb, Integer limit, Integer offset) {
        if (limit == null && offset == null) {
            return;
        }
        if (offset != null) {
            VKAssert.isTrue(offset >= 0, "Offset must be >= 0");
            sb.append(" OFFSET ").append(offset).append(" ROWS");
        }
        if (limit != null) {
            VKAssert.isTrue(limit > 0, "Limit must be > 0");
            sb.append(" FETCH FIRST ").append(limit).append(" ROWS ONLY");
        }
    }
}
