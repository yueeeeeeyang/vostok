package yueyang.vostok.office.excel;

/** Excel 写入选项。值为 -1 表示沿用 Office 全局配置。 */
public final class VKExcelWriteOptions {
    private int maxSheets = -1;
    private int maxRowsPerSheet = -1;
    private int maxColsPerRow = -1;
    private int maxCellChars = -1;
    private long maxWorkbookBytes = -1;
    private String tempSubDir;

    /** 返回默认写入选项。 */
    public static VKExcelWriteOptions defaults() {
        return new VKExcelWriteOptions();
    }

    public int maxSheets() {
        return maxSheets;
    }

    public VKExcelWriteOptions maxSheets(int maxSheets) {
        this.maxSheets = maxSheets;
        return this;
    }

    public int maxRowsPerSheet() {
        return maxRowsPerSheet;
    }

    public VKExcelWriteOptions maxRowsPerSheet(int maxRowsPerSheet) {
        this.maxRowsPerSheet = maxRowsPerSheet;
        return this;
    }

    public int maxColsPerRow() {
        return maxColsPerRow;
    }

    public VKExcelWriteOptions maxColsPerRow(int maxColsPerRow) {
        this.maxColsPerRow = maxColsPerRow;
        return this;
    }

    public int maxCellChars() {
        return maxCellChars;
    }

    public VKExcelWriteOptions maxCellChars(int maxCellChars) {
        this.maxCellChars = maxCellChars;
        return this;
    }

    public long maxWorkbookBytes() {
        return maxWorkbookBytes;
    }

    public VKExcelWriteOptions maxWorkbookBytes(long maxWorkbookBytes) {
        this.maxWorkbookBytes = maxWorkbookBytes;
        return this;
    }

    public String tempSubDir() {
        return tempSubDir;
    }

    /** 临时解包子目录（相对 File.baseDir）。 */
    public VKExcelWriteOptions tempSubDir(String tempSubDir) {
        this.tempSubDir = tempSubDir;
        return this;
    }
}
