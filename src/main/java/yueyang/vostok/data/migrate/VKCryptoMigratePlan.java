package yueyang.vostok.data.migrate;

/**
 * 字段加解密迁移预览结果。
 */
public class VKCryptoMigratePlan {
    private final String mode;
    private final String dataSourceName;
    private final String table;
    private final String idColumn;
    private final String targetColumn;
    private final int batchSize;
    private final long maxRows;
    private final long estimatedRows;
    private final boolean transactionPerBatch;
    private final String note;

    public VKCryptoMigratePlan(String mode, String dataSourceName, String table, String idColumn, String targetColumn,
                               int batchSize, long maxRows, long estimatedRows, boolean transactionPerBatch, String note) {
        this.mode = mode;
        this.dataSourceName = dataSourceName;
        this.table = table;
        this.idColumn = idColumn;
        this.targetColumn = targetColumn;
        this.batchSize = batchSize;
        this.maxRows = maxRows;
        this.estimatedRows = estimatedRows;
        this.transactionPerBatch = transactionPerBatch;
        this.note = note;
    }

    public String getMode() {
        return mode;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public String getTable() {
        return table;
    }

    public String getIdColumn() {
        return idColumn;
    }

    public String getTargetColumn() {
        return targetColumn;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public long getMaxRows() {
        return maxRows;
    }

    public long getEstimatedRows() {
        return estimatedRows;
    }

    public boolean isTransactionPerBatch() {
        return transactionPerBatch;
    }

    public String getNote() {
        return note;
    }
}
