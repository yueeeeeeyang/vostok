package yueyang.vostok.office.convert;

/** Office 转换选项。 */
public final class VKOfficeConvertOptions {
    private boolean includeImages = true;
    private int maxTextCharsPerPage = 2000;
    private String csvDelimiter = ",";
    private String csvLineSeparator = "\n";
    private String csvCharset = "UTF-8";
    private String csvSheetName = "Sheet1";

    public static VKOfficeConvertOptions defaults() {
        return new VKOfficeConvertOptions();
    }

    public boolean includeImages() {
        return includeImages;
    }

    public VKOfficeConvertOptions includeImages(boolean includeImages) {
        this.includeImages = includeImages;
        return this;
    }

    public int maxTextCharsPerPage() {
        return maxTextCharsPerPage;
    }

    public VKOfficeConvertOptions maxTextCharsPerPage(int maxTextCharsPerPage) {
        this.maxTextCharsPerPage = maxTextCharsPerPage;
        return this;
    }

    public String csvDelimiter() {
        return csvDelimiter;
    }

    public VKOfficeConvertOptions csvDelimiter(String csvDelimiter) {
        this.csvDelimiter = csvDelimiter;
        return this;
    }

    public String csvLineSeparator() {
        return csvLineSeparator;
    }

    public VKOfficeConvertOptions csvLineSeparator(String csvLineSeparator) {
        this.csvLineSeparator = csvLineSeparator;
        return this;
    }

    public String csvCharset() {
        return csvCharset;
    }

    public VKOfficeConvertOptions csvCharset(String csvCharset) {
        this.csvCharset = csvCharset;
        return this;
    }

    public String csvSheetName() {
        return csvSheetName;
    }

    public VKOfficeConvertOptions csvSheetName(String csvSheetName) {
        this.csvSheetName = csvSheetName;
        return this;
    }
}
