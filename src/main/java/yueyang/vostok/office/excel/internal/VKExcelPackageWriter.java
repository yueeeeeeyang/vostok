package yueyang.vostok.office.excel.internal;

import yueyang.vostok.office.excel.VKExcelWorkbook;

import java.nio.file.Path;

/** 写入解包形态的 xlsx 目录。 */
public final class VKExcelPackageWriter {
    private final Path packageRoot;
    private final VKExcelLimits limits;

    public VKExcelPackageWriter(Path packageRoot, VKExcelLimits limits) {
        this.packageRoot = packageRoot;
        this.limits = limits;
    }

    public void writeWorkbook(VKExcelWorkbook workbook) {
        VKExcelXmlWriter.writePackage(packageRoot, workbook, limits);
    }
}
