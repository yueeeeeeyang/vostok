package yueyang.vostok.data.migrate;

import java.util.Arrays;

/**
 * 字段加解密迁移参数。
 */
public class VKCryptoMigrateOptions {
    private String dataSourceName;
    private String table;
    private String idColumn;
    private String targetColumn;
    private String whereSql;
    private Object[] whereParams = new Object[0];
    private int batchSize = 500;
    private long maxRows = 0;
    private boolean dryRun = false;
    private boolean skipOnError = false;
    private boolean useTransactionPerBatch = true;
    private String encryptKeyId;
    private boolean allowPlaintextRead = false;

    public String getDataSourceName() {
        return dataSourceName;
    }

    public VKCryptoMigrateOptions dataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
        return this;
    }

    public String getTable() {
        return table;
    }

    public VKCryptoMigrateOptions table(String table) {
        this.table = table;
        return this;
    }

    public String getIdColumn() {
        return idColumn;
    }

    public VKCryptoMigrateOptions idColumn(String idColumn) {
        this.idColumn = idColumn;
        return this;
    }

    public String getTargetColumn() {
        return targetColumn;
    }

    public VKCryptoMigrateOptions targetColumn(String targetColumn) {
        this.targetColumn = targetColumn;
        return this;
    }

    public String getWhereSql() {
        return whereSql;
    }

    public VKCryptoMigrateOptions whereSql(String whereSql) {
        this.whereSql = whereSql;
        return this;
    }

    public Object[] getWhereParams() {
        return whereParams == null ? new Object[0] : Arrays.copyOf(whereParams, whereParams.length);
    }

    public VKCryptoMigrateOptions whereParams(Object... whereParams) {
        this.whereParams = whereParams == null ? new Object[0] : Arrays.copyOf(whereParams, whereParams.length);
        return this;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public VKCryptoMigrateOptions batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public long getMaxRows() {
        return maxRows;
    }

    public VKCryptoMigrateOptions maxRows(long maxRows) {
        this.maxRows = maxRows;
        return this;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public VKCryptoMigrateOptions dryRun(boolean dryRun) {
        this.dryRun = dryRun;
        return this;
    }

    public boolean isSkipOnError() {
        return skipOnError;
    }

    public VKCryptoMigrateOptions skipOnError(boolean skipOnError) {
        this.skipOnError = skipOnError;
        return this;
    }

    public boolean isUseTransactionPerBatch() {
        return useTransactionPerBatch;
    }

    public VKCryptoMigrateOptions useTransactionPerBatch(boolean useTransactionPerBatch) {
        this.useTransactionPerBatch = useTransactionPerBatch;
        return this;
    }

    public String getEncryptKeyId() {
        return encryptKeyId;
    }

    public VKCryptoMigrateOptions encryptKeyId(String encryptKeyId) {
        this.encryptKeyId = encryptKeyId;
        return this;
    }

    public boolean isAllowPlaintextRead() {
        return allowPlaintextRead;
    }

    public VKCryptoMigrateOptions allowPlaintextRead(boolean allowPlaintextRead) {
        this.allowPlaintextRead = allowPlaintextRead;
        return this;
    }
}
