package yueyang.vostok.office.excel.internal;

import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.excel.VKExcelReadOptions;
import yueyang.vostok.office.excel.VKExcelWriteOptions;

/** Excel 读写限制参数快照。 */
public final class VKExcelLimits {
    private final int maxSheets;
    private final int maxRowsPerSheet;
    private final int maxColsPerRow;
    private final int maxCellChars;
    private final int maxSharedStrings;
    private final long maxWorkbookBytes;
    private final int xxeSampleBytes;
    private final String tempSubDir;

    private VKExcelLimits(int maxSheets,
                          int maxRowsPerSheet,
                          int maxColsPerRow,
                          int maxCellChars,
                          int maxSharedStrings,
                          long maxWorkbookBytes,
                          int xxeSampleBytes,
                          String tempSubDir) {
        this.maxSheets = maxSheets;
        this.maxRowsPerSheet = maxRowsPerSheet;
        this.maxColsPerRow = maxColsPerRow;
        this.maxCellChars = maxCellChars;
        this.maxSharedStrings = maxSharedStrings;
        this.maxWorkbookBytes = maxWorkbookBytes;
        this.xxeSampleBytes = xxeSampleBytes;
        this.tempSubDir = tempSubDir;
    }

    public static VKExcelLimits fromRead(VKOfficeConfig config, VKExcelReadOptions options) {
        VKExcelReadOptions opt = options == null ? VKExcelReadOptions.defaults() : options;
        return new VKExcelLimits(
                pick(opt.maxSheets(), config.getExcelMaxSheets()),
                pick(opt.maxRowsPerSheet(), config.getExcelMaxRowsPerSheet()),
                pick(opt.maxColsPerRow(), config.getExcelMaxColsPerRow()),
                pick(opt.maxCellChars(), config.getExcelMaxCellChars()),
                pick(opt.maxSharedStrings(), config.getExcelMaxSharedStrings()),
                pickLong(opt.maxWorkbookBytes(), config.getExcelMaxWorkbookBytes()),
                Math.max(1024, opt.xxeSampleBytes()),
                pickSubDir(opt.tempSubDir(), config.getExcelTempDir())
        );
    }

    public static VKExcelLimits fromWrite(VKOfficeConfig config, VKExcelWriteOptions options) {
        VKExcelWriteOptions opt = options == null ? VKExcelWriteOptions.defaults() : options;
        return new VKExcelLimits(
                pick(opt.maxSheets(), config.getExcelMaxSheets()),
                pick(opt.maxRowsPerSheet(), config.getExcelMaxRowsPerSheet()),
                pick(opt.maxColsPerRow(), config.getExcelMaxColsPerRow()),
                pick(opt.maxCellChars(), config.getExcelMaxCellChars()),
                config.getExcelMaxSharedStrings(),
                pickLong(opt.maxWorkbookBytes(), config.getExcelMaxWorkbookBytes()),
                8192,
                pickSubDir(opt.tempSubDir(), config.getExcelTempDir())
        );
    }

    private static int pick(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static long pickLong(long value, long fallback) {
        return value > 0 ? value : fallback;
    }

    private static String pickSubDir(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    public int maxSheets() {
        return maxSheets;
    }

    public int maxRowsPerSheet() {
        return maxRowsPerSheet;
    }

    public int maxColsPerRow() {
        return maxColsPerRow;
    }

    public int maxCellChars() {
        return maxCellChars;
    }

    public int maxSharedStrings() {
        return maxSharedStrings;
    }

    public long maxWorkbookBytes() {
        return maxWorkbookBytes;
    }

    public int xxeSampleBytes() {
        return xxeSampleBytes;
    }

    public String tempSubDir() {
        return tempSubDir;
    }
}
