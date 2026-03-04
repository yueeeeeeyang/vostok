package yueyang.vostok.office.excel.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Excel 模板渲染选项。 */
public final class VKExcelTemplateOptions {
    private boolean strictVariable;
    private boolean defaultKeepPlaceholderRows = true;
    private long maxExpandedRows = 200_000;
    private long maxOutputBytes = -1;
    private final List<String> targetSheets = new ArrayList<>();
    private String tempSubDir;

    public static VKExcelTemplateOptions defaults() {
        return new VKExcelTemplateOptions();
    }

    public boolean strictVariable() {
        return strictVariable;
    }

    public VKExcelTemplateOptions strictVariable(boolean strictVariable) {
        this.strictVariable = strictVariable;
        return this;
    }

    public boolean defaultKeepPlaceholderRows() {
        return defaultKeepPlaceholderRows;
    }

    public VKExcelTemplateOptions defaultKeepPlaceholderRows(boolean defaultKeepPlaceholderRows) {
        this.defaultKeepPlaceholderRows = defaultKeepPlaceholderRows;
        return this;
    }

    public long maxExpandedRows() {
        return maxExpandedRows;
    }

    public VKExcelTemplateOptions maxExpandedRows(long maxExpandedRows) {
        this.maxExpandedRows = maxExpandedRows;
        return this;
    }

    public long maxOutputBytes() {
        return maxOutputBytes;
    }

    public VKExcelTemplateOptions maxOutputBytes(long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
        return this;
    }

    public List<String> targetSheets() {
        return Collections.unmodifiableList(targetSheets);
    }

    public VKExcelTemplateOptions targetSheets(List<String> targetSheets) {
        this.targetSheets.clear();
        if (targetSheets != null) {
            for (String sheet : targetSheets) {
                if (sheet != null && !sheet.isBlank()) {
                    this.targetSheets.add(sheet.trim());
                }
            }
        }
        return this;
    }

    public VKExcelTemplateOptions addTargetSheet(String sheetName) {
        if (sheetName != null && !sheetName.isBlank()) {
            this.targetSheets.add(sheetName.trim());
        }
        return this;
    }

    public String tempSubDir() {
        return tempSubDir;
    }

    public VKExcelTemplateOptions tempSubDir(String tempSubDir) {
        this.tempSubDir = tempSubDir;
        return this;
    }
}
