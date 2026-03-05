package yueyang.vostok.data.core;

import yueyang.vostok.data.DataResult;
import yueyang.vostok.util.VKAssert;

/**
 * 原生 SQL 执行入口。
 */
public final class VostokSqlOps {
    private VostokSqlOps() {
    }

    /**
     * 执行原生查询 SQL，返回游标式结果。
     */
    public static DataResult executeQuery(String sql, Object... params) {
        VostokInternal.ensureInit();
        VKAssert.notBlank(sql, "SQL is blank");
        return VostokInternal.executeQueryResult(sql, params == null ? new Object[0] : params);
    }

    /**
     * 执行原生更新 SQL，返回影响行数。
     */
    public static int executeUpdate(String sql, Object... params) {
        VostokInternal.ensureInit();
        VKAssert.notBlank(sql, "SQL is blank");
        return VostokInternal.executeUpdateRaw(sql, params == null ? new Object[0] : params);
    }
}
