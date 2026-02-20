package yueyang.vostok.data.migrate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 字段加解密迁移执行结果。
 */
public class VKCryptoMigrateResult {
    private final long scannedRows;
    private final long updatedRows;
    private final long skippedRows;
    private final long failedRows;
    private final long costMs;
    private final List<String> errorsTopN;

    public VKCryptoMigrateResult(long scannedRows, long updatedRows, long skippedRows, long failedRows, long costMs, List<String> errorsTopN) {
        this.scannedRows = scannedRows;
        this.updatedRows = updatedRows;
        this.skippedRows = skippedRows;
        this.failedRows = failedRows;
        this.costMs = costMs;
        this.errorsTopN = errorsTopN == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(errorsTopN));
    }

    public long getScannedRows() {
        return scannedRows;
    }

    public long getUpdatedRows() {
        return updatedRows;
    }

    public long getSkippedRows() {
        return skippedRows;
    }

    public long getFailedRows() {
        return failedRows;
    }

    public long getCostMs() {
        return costMs;
    }

    public List<String> getErrorsTopN() {
        return errorsTopN;
    }
}
