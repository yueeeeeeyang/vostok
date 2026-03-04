package yueyang.vostok.office;

/**
 * Office 模块配置。
 *
 * <p>用于 Excel / Word / PPT / PDF 的读写限制与安全阈值控制。</p>
 */
public final class VKOfficeConfig {
    private int excelMaxSheets = 256;
    private int excelMaxRowsPerSheet = 1_000_000;
    private int excelMaxColsPerRow = 16_384;
    private int excelMaxCellChars = 32_767;
    private int excelMaxSharedStrings = 2_000_000;
    private long excelMaxWorkbookBytes = 512L * 1024 * 1024;
    /** Office 统一临时目录根路径（Excel/Word/PPT/PDF 在其下按子目录隔离）。 */
    private String officeTempDir = "tmp/office";
    private int xxeSampleBytes = 8192;

    private long wordMaxDocumentBytes = 512L * 1024 * 1024;
    private int wordMaxTextChars = 20_000_000;
    private int wordMaxImages = 10_000;
    private long wordMaxSingleImageBytes = 50L * 1024 * 1024;
    private long wordMaxTotalImageBytes = 512L * 1024 * 1024;

    private long pptMaxDocumentBytes = 512L * 1024 * 1024;
    private int pptMaxSlides = 10_000;
    private int pptMaxTextChars = 20_000_000;
    private int pptMaxImages = 10_000;
    private long pptMaxSingleImageBytes = 50L * 1024 * 1024;
    private long pptMaxTotalImageBytes = 512L * 1024 * 1024;

    private long pdfMaxDocumentBytes = 512L * 1024 * 1024;
    private int pdfMaxPages = 10_000;
    private int pdfMaxTextChars = 20_000_000;
    private int pdfMaxImages = 10_000;
    private long pdfMaxSingleImageBytes = 50L * 1024 * 1024;
    private long pdfMaxTotalImageBytes = 512L * 1024 * 1024;
    private int pdfMaxObjects = 1_000_000;
    private long pdfMaxStreamBytes = 128L * 1024 * 1024;

    private boolean officeJobEnabled = true;
    private int officeJobWorkerThreads = 4;
    private int officeJobQueueCapacity = 4096;
    private long officeJobRetentionMs = 24L * 60 * 60 * 1000;
    private long officeJobResultMaxBytes = 64L * 1024 * 1024;
    private boolean officeJobNotifyOnRunning = false;
    private int officeJobCallbackThreads = 2;
    private int officeJobCallbackQueueCapacity = 4096;
    private long officeJobCallbackTimeoutMs = 5000L;

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

    public String getOfficeTempDir() {
        return officeTempDir;
    }

    /** 配置统一临时目录根。 */
    public VKOfficeConfig officeTempDir(String officeTempDir) {
        this.officeTempDir = officeTempDir;
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


    public long getPptMaxDocumentBytes() {
        return pptMaxDocumentBytes;
    }

    public VKOfficeConfig pptMaxDocumentBytes(long pptMaxDocumentBytes) {
        this.pptMaxDocumentBytes = pptMaxDocumentBytes;
        return this;
    }

    public int getPptMaxSlides() {
        return pptMaxSlides;
    }

    public VKOfficeConfig pptMaxSlides(int pptMaxSlides) {
        this.pptMaxSlides = pptMaxSlides;
        return this;
    }

    public int getPptMaxTextChars() {
        return pptMaxTextChars;
    }

    public VKOfficeConfig pptMaxTextChars(int pptMaxTextChars) {
        this.pptMaxTextChars = pptMaxTextChars;
        return this;
    }

    public int getPptMaxImages() {
        return pptMaxImages;
    }

    public VKOfficeConfig pptMaxImages(int pptMaxImages) {
        this.pptMaxImages = pptMaxImages;
        return this;
    }

    public long getPptMaxSingleImageBytes() {
        return pptMaxSingleImageBytes;
    }

    public VKOfficeConfig pptMaxSingleImageBytes(long pptMaxSingleImageBytes) {
        this.pptMaxSingleImageBytes = pptMaxSingleImageBytes;
        return this;
    }

    public long getPptMaxTotalImageBytes() {
        return pptMaxTotalImageBytes;
    }

    public VKOfficeConfig pptMaxTotalImageBytes(long pptMaxTotalImageBytes) {
        this.pptMaxTotalImageBytes = pptMaxTotalImageBytes;
        return this;
    }


    public long getPdfMaxDocumentBytes() {
        return pdfMaxDocumentBytes;
    }

    public VKOfficeConfig pdfMaxDocumentBytes(long pdfMaxDocumentBytes) {
        this.pdfMaxDocumentBytes = pdfMaxDocumentBytes;
        return this;
    }

    public int getPdfMaxPages() {
        return pdfMaxPages;
    }

    public VKOfficeConfig pdfMaxPages(int pdfMaxPages) {
        this.pdfMaxPages = pdfMaxPages;
        return this;
    }

    public int getPdfMaxTextChars() {
        return pdfMaxTextChars;
    }

    public VKOfficeConfig pdfMaxTextChars(int pdfMaxTextChars) {
        this.pdfMaxTextChars = pdfMaxTextChars;
        return this;
    }

    public int getPdfMaxImages() {
        return pdfMaxImages;
    }

    public VKOfficeConfig pdfMaxImages(int pdfMaxImages) {
        this.pdfMaxImages = pdfMaxImages;
        return this;
    }

    public long getPdfMaxSingleImageBytes() {
        return pdfMaxSingleImageBytes;
    }

    public VKOfficeConfig pdfMaxSingleImageBytes(long pdfMaxSingleImageBytes) {
        this.pdfMaxSingleImageBytes = pdfMaxSingleImageBytes;
        return this;
    }

    public long getPdfMaxTotalImageBytes() {
        return pdfMaxTotalImageBytes;
    }

    public VKOfficeConfig pdfMaxTotalImageBytes(long pdfMaxTotalImageBytes) {
        this.pdfMaxTotalImageBytes = pdfMaxTotalImageBytes;
        return this;
    }

    public int getPdfMaxObjects() {
        return pdfMaxObjects;
    }

    public VKOfficeConfig pdfMaxObjects(int pdfMaxObjects) {
        this.pdfMaxObjects = pdfMaxObjects;
        return this;
    }

    public long getPdfMaxStreamBytes() {
        return pdfMaxStreamBytes;
    }

    public VKOfficeConfig pdfMaxStreamBytes(long pdfMaxStreamBytes) {
        this.pdfMaxStreamBytes = pdfMaxStreamBytes;
        return this;
    }


    public boolean getOfficeJobEnabled() {
        return officeJobEnabled;
    }

    public VKOfficeConfig officeJobEnabled(boolean officeJobEnabled) {
        this.officeJobEnabled = officeJobEnabled;
        return this;
    }

    public int getOfficeJobWorkerThreads() {
        return officeJobWorkerThreads;
    }

    public VKOfficeConfig officeJobWorkerThreads(int officeJobWorkerThreads) {
        this.officeJobWorkerThreads = officeJobWorkerThreads;
        return this;
    }

    public int getOfficeJobQueueCapacity() {
        return officeJobQueueCapacity;
    }

    public VKOfficeConfig officeJobQueueCapacity(int officeJobQueueCapacity) {
        this.officeJobQueueCapacity = officeJobQueueCapacity;
        return this;
    }

    public long getOfficeJobRetentionMs() {
        return officeJobRetentionMs;
    }

    public VKOfficeConfig officeJobRetentionMs(long officeJobRetentionMs) {
        this.officeJobRetentionMs = officeJobRetentionMs;
        return this;
    }

    public long getOfficeJobResultMaxBytes() {
        return officeJobResultMaxBytes;
    }

    public VKOfficeConfig officeJobResultMaxBytes(long officeJobResultMaxBytes) {
        this.officeJobResultMaxBytes = officeJobResultMaxBytes;
        return this;
    }

    public boolean getOfficeJobNotifyOnRunning() {
        return officeJobNotifyOnRunning;
    }

    public VKOfficeConfig officeJobNotifyOnRunning(boolean officeJobNotifyOnRunning) {
        this.officeJobNotifyOnRunning = officeJobNotifyOnRunning;
        return this;
    }

    public int getOfficeJobCallbackThreads() {
        return officeJobCallbackThreads;
    }

    public VKOfficeConfig officeJobCallbackThreads(int officeJobCallbackThreads) {
        this.officeJobCallbackThreads = officeJobCallbackThreads;
        return this;
    }

    public int getOfficeJobCallbackQueueCapacity() {
        return officeJobCallbackQueueCapacity;
    }

    public VKOfficeConfig officeJobCallbackQueueCapacity(int officeJobCallbackQueueCapacity) {
        this.officeJobCallbackQueueCapacity = officeJobCallbackQueueCapacity;
        return this;
    }

    public long getOfficeJobCallbackTimeoutMs() {
        return officeJobCallbackTimeoutMs;
    }

    public VKOfficeConfig officeJobCallbackTimeoutMs(long officeJobCallbackTimeoutMs) {
        this.officeJobCallbackTimeoutMs = officeJobCallbackTimeoutMs;
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
