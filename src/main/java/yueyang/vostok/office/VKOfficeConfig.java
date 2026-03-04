package yueyang.vostok.office;

/**
 * Office 模块配置。
 *
 * <p>当前用于 Excel / Word 读写限制与安全阈值控制。</p>
 */
public final class VKOfficeConfig {
    private int excelMaxSheets = 256;
    private int excelMaxRowsPerSheet = 1_000_000;
    private int excelMaxColsPerRow = 16_384;
    private int excelMaxCellChars = 32_767;
    private int excelMaxSharedStrings = 2_000_000;
    private long excelMaxWorkbookBytes = 512L * 1024 * 1024;
    private String excelTempDir = "tmp/excel";
    private int xxeSampleBytes = 8192;

    private long wordMaxDocumentBytes = 512L * 1024 * 1024;
    private int wordMaxTextChars = 20_000_000;
    private int wordMaxImages = 10_000;
    private long wordMaxSingleImageBytes = 50L * 1024 * 1024;
    private long wordMaxTotalImageBytes = 512L * 1024 * 1024;
    private String wordTempDir = "tmp/word";

    private long unzipMaxEntries = -1;
    private long unzipMaxTotalUncompressedBytes = -1;
    private long unzipMaxEntryUncompressedBytes = -1;

    public int getExcelMaxSheets() {
        return excelMaxSheets;
    }

    public VKOfficeConfig excelMaxSheets(int excelMaxSheets) {
        this.excelMaxSheets = excelMaxSheets;
        return this;
    }

    public int getExcelMaxRowsPerSheet() {
        return excelMaxRowsPerSheet;
    }

    public VKOfficeConfig excelMaxRowsPerSheet(int excelMaxRowsPerSheet) {
        this.excelMaxRowsPerSheet = excelMaxRowsPerSheet;
        return this;
    }

    public int getExcelMaxColsPerRow() {
        return excelMaxColsPerRow;
    }

    public VKOfficeConfig excelMaxColsPerRow(int excelMaxColsPerRow) {
        this.excelMaxColsPerRow = excelMaxColsPerRow;
        return this;
    }

    public int getExcelMaxCellChars() {
        return excelMaxCellChars;
    }

    public VKOfficeConfig excelMaxCellChars(int excelMaxCellChars) {
        this.excelMaxCellChars = excelMaxCellChars;
        return this;
    }

    public int getExcelMaxSharedStrings() {
        return excelMaxSharedStrings;
    }

    public VKOfficeConfig excelMaxSharedStrings(int excelMaxSharedStrings) {
        this.excelMaxSharedStrings = excelMaxSharedStrings;
        return this;
    }

    public long getExcelMaxWorkbookBytes() {
        return excelMaxWorkbookBytes;
    }

    public VKOfficeConfig excelMaxWorkbookBytes(long excelMaxWorkbookBytes) {
        this.excelMaxWorkbookBytes = excelMaxWorkbookBytes;
        return this;
    }

    public String getExcelTempDir() {
        return excelTempDir;
    }

    public VKOfficeConfig excelTempDir(String excelTempDir) {
        this.excelTempDir = excelTempDir;
        return this;
    }

    public int getXxeSampleBytes() {
        return xxeSampleBytes;
    }

    public VKOfficeConfig xxeSampleBytes(int xxeSampleBytes) {
        this.xxeSampleBytes = xxeSampleBytes;
        return this;
    }

    public long getWordMaxDocumentBytes() {
        return wordMaxDocumentBytes;
    }

    public VKOfficeConfig wordMaxDocumentBytes(long wordMaxDocumentBytes) {
        this.wordMaxDocumentBytes = wordMaxDocumentBytes;
        return this;
    }

    public int getWordMaxTextChars() {
        return wordMaxTextChars;
    }

    public VKOfficeConfig wordMaxTextChars(int wordMaxTextChars) {
        this.wordMaxTextChars = wordMaxTextChars;
        return this;
    }

    public int getWordMaxImages() {
        return wordMaxImages;
    }

    public VKOfficeConfig wordMaxImages(int wordMaxImages) {
        this.wordMaxImages = wordMaxImages;
        return this;
    }

    public long getWordMaxSingleImageBytes() {
        return wordMaxSingleImageBytes;
    }

    public VKOfficeConfig wordMaxSingleImageBytes(long wordMaxSingleImageBytes) {
        this.wordMaxSingleImageBytes = wordMaxSingleImageBytes;
        return this;
    }

    public long getWordMaxTotalImageBytes() {
        return wordMaxTotalImageBytes;
    }

    public VKOfficeConfig wordMaxTotalImageBytes(long wordMaxTotalImageBytes) {
        this.wordMaxTotalImageBytes = wordMaxTotalImageBytes;
        return this;
    }

    public String getWordTempDir() {
        return wordTempDir;
    }

    public VKOfficeConfig wordTempDir(String wordTempDir) {
        this.wordTempDir = wordTempDir;
        return this;
    }

    public long getUnzipMaxEntries() {
        return unzipMaxEntries;
    }

    public VKOfficeConfig unzipMaxEntries(long unzipMaxEntries) {
        this.unzipMaxEntries = unzipMaxEntries;
        return this;
    }

    public long getUnzipMaxTotalUncompressedBytes() {
        return unzipMaxTotalUncompressedBytes;
    }

    public VKOfficeConfig unzipMaxTotalUncompressedBytes(long unzipMaxTotalUncompressedBytes) {
        this.unzipMaxTotalUncompressedBytes = unzipMaxTotalUncompressedBytes;
        return this;
    }

    public long getUnzipMaxEntryUncompressedBytes() {
        return unzipMaxEntryUncompressedBytes;
    }

    public VKOfficeConfig unzipMaxEntryUncompressedBytes(long unzipMaxEntryUncompressedBytes) {
        this.unzipMaxEntryUncompressedBytes = unzipMaxEntryUncompressedBytes;
        return this;
    }
}
