package yueyang.vostok.office.excel;

/** Excel 读取选项。值为 -1 表示沿用 Office 全局配置。 */
public final class VKExcelReadOptions {
    private int maxSheets = -1;
    private int maxRowsPerSheet = -1;
    private int maxColsPerRow = -1;
    private int maxCellChars = -1;
    private int maxSharedStrings = -1;
    private long maxWorkbookBytes = -1;
    private String tempSubDir;
    private int xxeSampleBytes = 8192;

    /** 返回默认读取选项。 */
    public static VKExcelReadOptions defaults() {
        return new VKExcelReadOptions();
    }

    public int maxSheets() {
        return maxSheets;
    }

    public VKExcelReadOptions maxSheets(int maxSheets) {
        this.maxSheets = maxSheets;
        return this;
    }

    public int maxRowsPerSheet() {
        return maxRowsPerSheet;
    }

    public VKExcelReadOptions maxRowsPerSheet(int maxRowsPerSheet) {
        this.maxRowsPerSheet = maxRowsPerSheet;
        return this;
    }

    public int maxColsPerRow() {
        return maxColsPerRow;
    }

    public VKExcelReadOptions maxColsPerRow(int maxColsPerRow) {
        this.maxColsPerRow = maxColsPerRow;
        return this;
    }

    public int maxCellChars() {
        return maxCellChars;
    }

    public VKExcelReadOptions maxCellChars(int maxCellChars) {
        this.maxCellChars = maxCellChars;
        return this;
    }

    public int maxSharedStrings() {
        return maxSharedStrings;
    }

    public VKExcelReadOptions maxSharedStrings(int maxSharedStrings) {
        this.maxSharedStrings = maxSharedStrings;
        return this;
    }

    public long maxWorkbookBytes() {
        return maxWorkbookBytes;
    }

    public VKExcelReadOptions maxWorkbookBytes(long maxWorkbookBytes) {
        this.maxWorkbookBytes = maxWorkbookBytes;
        return this;
    }

    public String tempSubDir() {
        return tempSubDir;
    }

    /** 临时解包子目录（相对 File.baseDir）。 */
    public VKExcelReadOptions tempSubDir(String tempSubDir) {
        this.tempSubDir = tempSubDir;
        return this;
    }

    public int xxeSampleBytes() {
        return xxeSampleBytes;
    }

    /** XML 安全采样字节数。 */
    public VKExcelReadOptions xxeSampleBytes(int xxeSampleBytes) {
        this.xxeSampleBytes = xxeSampleBytes;
        return this;
    }
}
