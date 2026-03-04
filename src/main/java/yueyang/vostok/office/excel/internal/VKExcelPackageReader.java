package yueyang.vostok.office.excel.internal;

import yueyang.vostok.office.excel.VKExcelRowView;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.excel.VKExcelWorkbook;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 读取解包后的 xlsx 目录。 */
public final class VKExcelPackageReader {
    private final Path packageRoot;
    private final VKExcelLimits limits;

    public VKExcelPackageReader(Path packageRoot, VKExcelLimits limits) {
        this.packageRoot = packageRoot;
        this.limits = limits;
    }

    public VKExcelWorkbook readWorkbook() {
        VKExcelWorkbook wb = new VKExcelWorkbook();
        Path workbookXml = packageRoot.resolve("xl/workbook.xml");
        Path workbookRels = packageRoot.resolve("xl/_rels/workbook.xml.rels");
        requireFile(workbookXml, "Missing xl/workbook.xml");
        requireFile(workbookRels, "Missing xl/_rels/workbook.xml.rels");

        VKExcelSecurityGuard.assertSafeXmlSample(workbookXml, limits.xxeSampleBytes());
        VKExcelSecurityGuard.assertSafeXmlSample(workbookRels, limits.xxeSampleBytes());

        List<VKExcelXmlReader.WorkbookSheetRef> sheets = VKExcelXmlReader.readWorkbookSheetRefs(workbookXml, limits);
        Map<String, String> rels = VKExcelXmlReader.readWorkbookRels(workbookRels);

        Path sharedStringsXml = packageRoot.resolve("xl/sharedStrings.xml");
        VKExcelSecurityGuard.assertSafeXmlSample(sharedStringsXml, limits.xxeSampleBytes());
        List<String> sharedStrings = VKExcelXmlReader.readSharedStrings(sharedStringsXml, limits);

        for (VKExcelXmlReader.WorkbookSheetRef sheetRef : sheets) {
            Path sheetPath = resolveSheetPath(sheetRef.relId(), rels);
            VKExcelSecurityGuard.assertSafeXmlSample(sheetPath, limits.xxeSampleBytes());
            VKExcelSheet sheet = VKExcelXmlReader.readSheet(sheetPath, sheetRef.name(), sharedStrings, limits);
            wb.addSheet(sheet);
        }
        return wb;
    }

    public void readRows(String sheetName, Consumer<VKExcelRowView> consumer) {
        if (consumer == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "Row consumer is null");
        }
        Path workbookXml = packageRoot.resolve("xl/workbook.xml");
        Path workbookRels = packageRoot.resolve("xl/_rels/workbook.xml.rels");
        requireFile(workbookXml, "Missing xl/workbook.xml");
        requireFile(workbookRels, "Missing xl/_rels/workbook.xml.rels");

        VKExcelSecurityGuard.assertSafeXmlSample(workbookXml, limits.xxeSampleBytes());
        VKExcelSecurityGuard.assertSafeXmlSample(workbookRels, limits.xxeSampleBytes());

        List<VKExcelXmlReader.WorkbookSheetRef> sheetRefs = VKExcelXmlReader.readWorkbookSheetRefs(workbookXml, limits);
        Map<String, String> rels = VKExcelXmlReader.readWorkbookRels(workbookRels);

        VKExcelXmlReader.WorkbookSheetRef selected = selectSheet(sheetRefs, sheetName);
        Path sharedStringsXml = packageRoot.resolve("xl/sharedStrings.xml");
        VKExcelSecurityGuard.assertSafeXmlSample(sharedStringsXml, limits.xxeSampleBytes());
        List<String> sharedStrings = VKExcelXmlReader.readSharedStrings(sharedStringsXml, limits);

        Path sheetPath = resolveSheetPath(selected.relId(), rels);
        VKExcelSecurityGuard.assertSafeXmlSample(sheetPath, limits.xxeSampleBytes());
        VKExcelXmlReader.readSheetRows(sheetPath, sharedStrings, limits, consumer);
    }

    private VKExcelXmlReader.WorkbookSheetRef selectSheet(List<VKExcelXmlReader.WorkbookSheetRef> refs, String sheetName) {
        if (refs.isEmpty()) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR, "Workbook has no sheet");
        }
        if (sheetName == null || sheetName.isBlank()) {
            return refs.get(0);
        }
        for (VKExcelXmlReader.WorkbookSheetRef ref : refs) {
            if (sheetName.equals(ref.name())) {
                return ref;
            }
        }
        throw new VKOfficeException(VKOfficeErrorCode.NOT_FOUND, "Sheet not found: " + sheetName);
    }

    private Path resolveSheetPath(String relId, Map<String, String> rels) {
        String target = rels.get(relId);
        if (target == null || target.isBlank()) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "Sheet relationship target not found for relId=" + relId);
        }
        Path sheetPath = packageRoot.resolve("xl").resolve(target).normalize();
        if (!sheetPath.startsWith(packageRoot.resolve("xl").normalize())) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Sheet path escapes package root: " + target);
        }
        requireFile(sheetPath, "Missing sheet xml: " + target);
        return sheetPath;
    }

    private void requireFile(Path path, String message) {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR, message);
        }
    }
}
