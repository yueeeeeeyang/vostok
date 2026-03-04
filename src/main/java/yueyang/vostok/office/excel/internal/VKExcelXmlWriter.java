package yueyang.vostok.office.excel.internal;

import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.excel.VKExcelCellType;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.excel.VKExcelWorkbook;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 生成最小可用 OOXML。 */
public final class VKExcelXmlWriter {

    private VKExcelXmlWriter() {
    }

    public static void writePackage(Path packageRoot, VKExcelWorkbook workbook, VKExcelLimits limits) {
        if (workbook == null) {
            throw write("Workbook is null", null);
        }
        List<VKExcelSheet> sheets = workbook.sheets();
        if (sheets.isEmpty()) {
            throw write("Workbook sheets is empty", null);
        }
        if (sheets.size() > limits.maxSheets()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Excel sheets exceed limit: " + limits.maxSheets());
        }

        Path relsDir = packageRoot.resolve("_rels");
        Path xlDir = packageRoot.resolve("xl");
        Path xlRels = xlDir.resolve("_rels");
        Path xlWorksheets = xlDir.resolve("worksheets");

        try {
            Files.createDirectories(relsDir);
            Files.createDirectories(xlRels);
            Files.createDirectories(xlWorksheets);

            writeRootRels(relsDir.resolve(".rels"));
            writeContentTypes(packageRoot.resolve("[Content_Types].xml"), sheets.size());
            writeWorkbookXml(xlDir.resolve("workbook.xml"), sheets);
            writeWorkbookRels(xlRels.resolve("workbook.xml.rels"), sheets.size());
            writeStyles(xlDir.resolve("styles.xml"));

            for (int i = 0; i < sheets.size(); i++) {
                writeSheetXml(xlWorksheets.resolve("sheet" + (i + 1) + ".xml"), sheets.get(i), limits);
            }
        } catch (VKOfficeException e) {
            throw e;
        } catch (Exception e) {
            throw write("Write excel package failed", e);
        }
    }

    private static void writeRootRels(Path relsPath) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(relsPath, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            w.write("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>");
            w.write("</Relationships>");
        }
    }

    private static void writeContentTypes(Path path, int sheetCount) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
            w.write("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
            w.write("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
            w.write("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
            w.write("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
            for (int i = 1; i <= sheetCount; i++) {
                w.write("<Override PartName=\"/xl/worksheets/sheet" + i + ".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
            }
            w.write("</Types>");
        }
    }

    private static void writeWorkbookXml(Path path, List<VKExcelSheet> sheets) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ");
            w.write("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
            w.write("<sheets>");
            for (int i = 0; i < sheets.size(); i++) {
                String name = escapeXmlAttr(sheets.get(i).name());
                w.write("<sheet name=\"" + name + "\" sheetId=\"" + (i + 1) + "\" r:id=\"rId" + (i + 1) + "\"/>");
            }
            w.write("</sheets>");
            w.write("</workbook>");
        }
    }

    private static void writeWorkbookRels(Path path, int sheetCount) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for (int i = 1; i <= sheetCount; i++) {
                w.write("<Relationship Id=\"rId" + i + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet" + i + ".xml\"/>");
            }
            w.write("<Relationship Id=\"rId" + (sheetCount + 1) + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
            w.write("</Relationships>");
        }
    }

    private static void writeStyles(Path path) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            w.write("<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>");
            w.write("<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>");
            w.write("<borders count=\"1\"><border/></borders>");
            w.write("<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>");
            w.write("<cellXfs count=\"1\"><xf xfId=\"0\"/></cellXfs>");
            w.write("</styleSheet>");
        }
    }

    private static void writeSheetXml(Path path, VKExcelSheet sheet, VKExcelLimits limits) throws IOException {
        if (sheet == null) {
            throw write("Sheet is null", null);
        }
        if (sheet.rowCount() > limits.maxRowsPerSheet()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Excel rows exceed limit: " + limits.maxRowsPerSheet());
        }
        if (sheet.maxColumnIndex() > limits.maxColsPerRow()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Excel columns exceed limit: " + limits.maxColsPerRow());
        }

        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            w.write("<sheetData>");

            List<Integer> rowIndexes = new ArrayList<>(sheet.rows().keySet());
            Collections.sort(rowIndexes);
            for (Integer rowIndex : rowIndexes) {
                if (rowIndex == null || rowIndex <= 0) {
                    continue;
                }
                w.write("<row r=\"" + rowIndex + "\">");
                Map<Integer, VKExcelCell> rowCells = sheet.rows().get(rowIndex);
                List<Integer> colIndexes = new ArrayList<>(rowCells.keySet());
                Collections.sort(colIndexes);
                for (Integer col : colIndexes) {
                    if (col == null || col <= 0) {
                        continue;
                    }
                    VKExcelCell cell = rowCells.get(col);
                    writeCell(w, cell, limits);
                }
                w.write("</row>");
            }

            w.write("</sheetData>");
            w.write("</worksheet>");
        }
    }

    private static void writeCell(BufferedWriter w, VKExcelCell cell, VKExcelLimits limits) throws IOException {
        if (cell == null) {
            return;
        }
        ensureCellChars(cell.value(), limits);
        ensureCellChars(cell.formula(), limits);

        String ref = VKCellRef.toRef(cell.rowIndex(), cell.columnIndex());
        VKExcelCellType type = cell.type();
        if (type == VKExcelCellType.BLANK) {
            w.write("<c r=\"" + ref + "\"/>");
            return;
        }
        if (type == VKExcelCellType.STRING) {
            String v = orEmpty(cell.value());
            w.write("<c r=\"" + ref + "\" t=\"inlineStr\"><is><t");
            if (needPreserveSpace(v)) {
                w.write(" xml:space=\"preserve\"");
            }
            w.write(">" + escapeXmlText(v) + "</t></is></c>");
            return;
        }
        if (type == VKExcelCellType.BOOLEAN) {
            String v = ("1".equals(cell.value()) || "true".equalsIgnoreCase(cell.value())) ? "1" : "0";
            w.write("<c r=\"" + ref + "\" t=\"b\"><v>" + v + "</v></c>");
            return;
        }
        if (type == VKExcelCellType.FORMULA) {
            w.write("<c r=\"" + ref + "\"><f>" + escapeXmlText(orEmpty(cell.formula())) + "</f>");
            if (cell.value() != null) {
                w.write("<v>" + escapeXmlText(cell.value()) + "</v>");
            }
            w.write("</c>");
            return;
        }
        w.write("<c r=\"" + ref + "\"><v>" + escapeXmlText(orEmpty(cell.value())) + "</v></c>");
    }

    private static void ensureCellChars(String value, VKExcelLimits limits) {
        if (value != null && value.length() > limits.maxCellChars()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Excel cell chars exceed limit: " + limits.maxCellChars());
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean needPreserveSpace(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return Character.isWhitespace(value.charAt(0))
                || Character.isWhitespace(value.charAt(value.length() - 1));
    }

    private static String escapeXmlText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escapeXmlAttr(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static VKOfficeException write(String message, Exception cause) {
        return cause == null
                ? new VKOfficeException(VKOfficeErrorCode.WRITE_ERROR, message)
                : new VKOfficeException(VKOfficeErrorCode.WRITE_ERROR, message, cause);
    }
}
