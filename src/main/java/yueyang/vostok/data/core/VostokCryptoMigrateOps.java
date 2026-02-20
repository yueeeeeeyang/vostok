package yueyang.vostok.data.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.data.exception.VKArgumentException;
import yueyang.vostok.data.exception.VKException;
import yueyang.vostok.data.migrate.VKCryptoMigrateOptions;
import yueyang.vostok.data.migrate.VKCryptoMigratePlan;
import yueyang.vostok.data.migrate.VKCryptoMigrateResult;
import yueyang.vostok.data.sql.VKSqlWhitelist;
import yueyang.vostok.util.VKAssert;
import yueyang.vostok.util.VKNameValidator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 字段加解密迁移能力。
 */
public final class VostokCryptoMigrateOps {
    private static final String CIPHER_PREFIX = "vk1:aes:";
    private static final int ERROR_TOP_N = 20;

    private VostokCryptoMigrateOps() {
    }

    public static VKCryptoMigrateResult encryptColumn(VKCryptoMigrateOptions options) {
        return runInDataSource(options, true, false);
    }

    public static VKCryptoMigrateResult decryptColumn(VKCryptoMigrateOptions options) {
        return runInDataSource(options, false, false);
    }

    public static VKCryptoMigratePlan previewEncrypt(VKCryptoMigrateOptions options) {
        return planInDataSource(options, true);
    }

    public static VKCryptoMigratePlan previewDecrypt(VKCryptoMigrateOptions options) {
        return planInDataSource(options, false);
    }

    private static VKCryptoMigrateResult runInDataSource(VKCryptoMigrateOptions options, boolean encrypt, boolean previewOnly) {
        validate(options, encrypt);
        VostokInternal.ensureInit();
        String ds = options.getDataSourceName();
        if (ds == null || ds.isBlank()) {
            return doRun(options, encrypt, previewOnly);
        }
        return VostokBootstrap.withDataSource(ds, () -> doRun(options, encrypt, previewOnly));
    }

    private static VKCryptoMigratePlan planInDataSource(VKCryptoMigrateOptions options, boolean encrypt) {
        validate(options, encrypt);
        VostokInternal.ensureInit();
        String ds = options.getDataSourceName();
        if (ds == null || ds.isBlank()) {
            return doPlan(options, encrypt);
        }
        return VostokBootstrap.withDataSource(ds, () -> doPlan(options, encrypt));
    }

    private static VKCryptoMigratePlan doPlan(VKCryptoMigrateOptions options, boolean encrypt) {
        validateWhereInCurrentDs(options);
        long estimated = countCandidates(options);
        String ds = currentDsName(options);
        String note = "估算行数为 where + 非空筛选结果，实际更新量取决于当前值是否已是目标状态。";
        return new VKCryptoMigratePlan(encrypt ? "ENCRYPT" : "DECRYPT", ds, options.getTable(),
                options.getIdColumn(), options.getTargetColumn(), options.getBatchSize(), options.getMaxRows(),
                estimated, options.isUseTransactionPerBatch(), note);
    }

    private static VKCryptoMigrateResult doRun(VKCryptoMigrateOptions options, boolean encrypt, boolean previewOnly) {
        validateWhereInCurrentDs(options);
        long start = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        long scanned = 0L;
        long updated = 0L;
        long skipped = 0L;
        long failed = 0L;
        long remain = options.getMaxRows() > 0 ? options.getMaxRows() : Long.MAX_VALUE;
        Object lastId = null;
        String updateSql = buildUpdateSql(options);

        while (remain > 0) {
            int pageSize = (int) Math.min(options.getBatchSize(), remain);
            List<Row> rows;
            try {
                rows = fetchPage(options, lastId, pageSize);
            } catch (Exception e) {
                throw new VKException(yueyang.vostok.data.exception.VKErrorCode.SQL_ERROR, "Migration select failed", e);
            }
            if (rows.isEmpty()) {
                break;
            }

            BatchOutcome outcome = processBatch(options, encrypt, previewOnly, updateSql, rows, errors);
            scanned += rows.size();
            updated += outcome.updated;
            skipped += outcome.skipped;
            failed += outcome.failed;

            lastId = rows.get(rows.size() - 1).id;
            remain -= rows.size();
            if (failed > 0 && !options.isSkipOnError()) {
                break;
            }
        }

        long cost = System.currentTimeMillis() - start;
        return new VKCryptoMigrateResult(scanned, updated, skipped, failed, cost, errors);
    }

    private static BatchOutcome processBatch(VKCryptoMigrateOptions options, boolean encrypt, boolean previewOnly, String updateSql,
                                             List<Row> rows, List<String> errors) {
        if (previewOnly || options.isDryRun()) {
            long updated = 0L;
            long skipped = 0L;
            long failed = 0L;
            for (Row row : rows) {
                try {
                    String transformed = transform(options, encrypt, row.value);
                    if (transformed == null) {
                        skipped++;
                    } else {
                        updated++;
                    }
                } catch (Exception e) {
                    failed++;
                    addError(errors, "id=" + row.id + ", err=" + e.getMessage());
                    if (!options.isSkipOnError()) {
                        throw e;
                    }
                }
            }
            return new BatchOutcome(updated, skipped, failed);
        }

        long updated = 0L;
        long skipped = 0L;
        long failed = 0L;
        try (Connection conn = VostokInternal.currentHolder().getDataSource().getConnection()) {
            boolean tx = options.isUseTransactionPerBatch();
            boolean originalAutoCommit = conn.getAutoCommit();
            if (tx) {
                conn.setAutoCommit(false);
            }
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                for (Row row : rows) {
                    String transformed;
                    try {
                        transformed = transform(options, encrypt, row.value);
                    } catch (Exception e) {
                        failed++;
                        addError(errors, "id=" + row.id + ", err=" + e.getMessage());
                        if (!options.isSkipOnError()) {
                            throw e;
                        }
                        continue;
                    }
                    if (transformed == null) {
                        skipped++;
                        continue;
                    }
                    try {
                        ps.setString(1, transformed);
                        ps.setObject(2, row.id);
                        int count = ps.executeUpdate();
                        if (count > 0) {
                            updated++;
                        } else {
                            skipped++;
                        }
                    } catch (Exception e) {
                        failed++;
                        addError(errors, "id=" + row.id + ", err=" + e.getMessage());
                        if (!options.isSkipOnError()) {
                            throw e;
                        }
                    }
                }
                if (tx) {
                    conn.commit();
                }
            } catch (Exception e) {
                if (tx) {
                    try {
                        conn.rollback();
                    } catch (Exception ignore) {
                        // ignored
                    }
                }
                throw e;
            } finally {
                if (tx) {
                    conn.setAutoCommit(originalAutoCommit);
                }
            }
            return new BatchOutcome(updated, skipped, failed);
        } catch (Exception e) {
            if (e instanceof VKException) {
                throw (VKException) e;
            }
            throw new VKException(yueyang.vostok.data.exception.VKErrorCode.SQL_ERROR, "Migration update failed", e);
        }
    }

    private static String transform(VKCryptoMigrateOptions options, boolean encrypt, Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new VKArgumentException("Target column value must be String");
        }
        String text = (String) value;
        if (encrypt) {
            if (text.startsWith(CIPHER_PREFIX)) {
                return null;
            }
            return Vostok.Security.encryptWithKeyId(text, options.getEncryptKeyId());
        }
        if (!text.startsWith(CIPHER_PREFIX)) {
            if (options.isAllowPlaintextRead()) {
                return null;
            }
            throw new VKArgumentException("Plaintext found but allowPlaintextRead=false");
        }
        return Vostok.Security.decryptWithKeyId(text);
    }

    private static List<Row> fetchPage(VKCryptoMigrateOptions options, Object lastId, int pageSize) throws Exception {
        List<Row> list = new ArrayList<>();
        String selectSql = buildSelectSql(options, lastId != null);
        try (Connection conn = VostokInternal.currentHolder().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            int idx = 1;
            if (lastId != null) {
                ps.setObject(idx++, lastId);
            }
            for (Object param : options.getWhereParams()) {
                ps.setObject(idx++, param);
            }
            ps.setMaxRows(pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Row(rs.getObject(1), rs.getObject(2)));
                }
            }
        }
        return list;
    }

    private static long countCandidates(VKCryptoMigrateOptions options) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(1) FROM ").append(options.getTable())
                .append(" WHERE ").append(options.getTargetColumn()).append(" IS NOT NULL");
        if (hasWhere(options)) {
            sql.append(" AND (").append(options.getWhereSql()).append(")");
        }
        try {
            Object value = VostokInternal.currentExecutor().queryScalar(sql.toString(), options.getWhereParams());
            long rows = (value instanceof Number) ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
            if (options.getMaxRows() > 0) {
                return Math.min(rows, options.getMaxRows());
            }
            return rows;
        } catch (Exception e) {
            throw new VKException(yueyang.vostok.data.exception.VKErrorCode.SQL_ERROR, "Migration preview count failed", e);
        }
    }

    private static String buildSelectSql(VKCryptoMigrateOptions options, boolean includeCursor) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(options.getIdColumn()).append(", ").append(options.getTargetColumn())
                .append(" FROM ").append(options.getTable())
                .append(" WHERE ").append(options.getTargetColumn()).append(" IS NOT NULL");
        if (includeCursor) {
            sql.append(" AND ").append(options.getIdColumn()).append(" > ?");
        }
        if (hasWhere(options)) {
            sql.append(" AND (").append(options.getWhereSql()).append(")");
        }
        sql.append(" ORDER BY ").append(options.getIdColumn()).append(" ASC");
        return sql.toString();
    }

    private static String buildUpdateSql(VKCryptoMigrateOptions options) {
        return "UPDATE " + options.getTable() + " SET " + options.getTargetColumn() + " = ? WHERE " + options.getIdColumn() + " = ?";
    }

    private static void validate(VKCryptoMigrateOptions options, boolean encrypt) {
        VKAssert.notNull(options, "VKCryptoMigrateOptions is null");
        VKNameValidator.validate(options.getTable(), "Table");
        VKNameValidator.validate(options.getIdColumn(), "Id column");
        VKNameValidator.validate(options.getTargetColumn(), "Target column");
        VKAssert.isTrue(options.getBatchSize() > 0, "batchSize must be > 0");
        VKAssert.isTrue(options.getMaxRows() >= 0, "maxRows must be >= 0");
        if (encrypt) {
            VKAssert.notBlank(options.getEncryptKeyId(), "encryptKeyId is blank");
        }
    }

    private static void validateWhereInCurrentDs(VKCryptoMigrateOptions options) {
        if (!hasWhere(options)) {
            return;
        }
        VKAssert.isTrue(VKSqlWhitelist.allowRaw(options.getWhereSql()),
                "whereSql not in whitelist for current data source");
    }

    private static boolean hasWhere(VKCryptoMigrateOptions options) {
        String where = options.getWhereSql();
        return where != null && !where.isBlank();
    }

    private static String currentDsName(VKCryptoMigrateOptions options) {
        String ds = options.getDataSourceName();
        if (ds != null && !ds.isBlank()) {
            return ds;
        }
        return VostokInternal.currentDataSourceName();
    }

    private static void addError(List<String> errors, String msg) {
        if (errors.size() < ERROR_TOP_N) {
            errors.add(msg);
        }
    }

    private static class Row {
        private final Object id;
        private final Object value;

        private Row(Object id, Object value) {
            this.id = id;
            this.value = value;
        }
    }

    private static class BatchOutcome {
        private final long updated;
        private final long skipped;
        private final long failed;

        private BatchOutcome(long updated, long skipped, long failed) {
            this.updated = updated;
            this.skipped = skipped;
            this.failed = failed;
        }
    }
}
