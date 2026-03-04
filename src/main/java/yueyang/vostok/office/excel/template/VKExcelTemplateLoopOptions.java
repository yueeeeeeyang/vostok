package yueyang.vostok.office.excel.template;

/** Excel 模板循环默认选项。 */
public final class VKExcelTemplateLoopOptions {
    /**
     * 是否保留循环起止占位行。
     *
     * <p>默认 true。若标记行显式指定 keepPlaceholderRows 参数，则以标记参数为准。</p>
     */
    private boolean keepPlaceholderRows = true;

    public static VKExcelTemplateLoopOptions defaults() {
        return new VKExcelTemplateLoopOptions();
    }

    public boolean keepPlaceholderRows() {
        return keepPlaceholderRows;
    }

    public VKExcelTemplateLoopOptions keepPlaceholderRows(boolean keepPlaceholderRows) {
        this.keepPlaceholderRows = keepPlaceholderRows;
        return this;
    }
}
