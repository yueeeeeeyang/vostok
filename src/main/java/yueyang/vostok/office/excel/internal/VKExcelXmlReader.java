package yueyang.vostok.office.excel.internal;

import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.excel.VKExcelCellType;
import yueyang.vostok.office.excel.VKExcelRowView;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 基于 StAX 的 Excel XML 读取器。 */
public final class VKExcelXmlReader {

    private VKExcelXmlReader() {
    }

    public record WorkbookSheetRef(String name, String relId) {
    }

    public static List<WorkbookSheetRef> readWorkbookSheetRefs(Path workbookXml, VKExcelLimits limits) {
        List<WorkbookSheetRef> refs = new ArrayList<>();
        XMLInputFactory factory = VKXmlSafeFactory.createInputFactory();
        try (InputStream in = Files.newInputStream(workbookXml)) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            while (r.hasNext()) {
                int e = r.next();
                if (e == XMLStreamConstants.START_ELEMENT && "sheet".equals(r.getLocalName())) {
                    String name = attr(r, "name");
                    String relId = attr(r, "id"); // r:id 的 localName 仍为 id
                    if (name != null && relId != null) {
                        refs.add(new WorkbookSheetRef(name, relId));
                        if (refs.size() > limits.maxSheets()) {
                            throw limit("Excel sheets exceed limit: " + limits.maxSheets());
                        }
                    }
                }
            }
            return refs;
        } catch (VKOfficeException e) {
            throw e;
        } catch (Exception e) {
            throw parse("Read workbook.xml failed", e);
        }
    }

    public static Map<String, String> readWorkbookRels(Path workbookRels) {
        Map<String, String> rels = new HashMap<>();
        XMLInputFactory factory = VKXmlSafeFactory.createInputFactory();
        try (InputStream in = Files.newInputStream(workbookRels)) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            while (r.hasNext()) {
                int e = r.next();
                if (e == XMLStreamConstants.START_ELEMENT && "Relationship".equals(r.getLocalName())) {
                    String id = attr(r, "Id");
                    String target = attr(r, "Target");
                    if (id != null && target != null) {
                        rels.put(id, target);
                    }
                }
            }
            return rels;
        } catch (Exception e) {
            throw parse("Read workbook rels failed", e);
        }
    }

    public static List<String> readSharedStrings(Path sharedStringsXml, VKExcelLimits limits) {
        List<String> values = new ArrayList<>();
        if (sharedStringsXml == null || !Files.exists(sharedStringsXml)) {
            return values;
        }
        XMLInputFactory factory = VKXmlSafeFactory.createInputFactory();
        try (InputStream in = Files.newInputStream(sharedStringsXml)) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            while (r.hasNext()) {
                int e = r.next();
                if (e == XMLStreamConstants.START_ELEMENT && "si".equals(r.getLocalName())) {
                    String value = readSharedStringItem(r);
                    ensureCellChars(value, limits);
                    values.add(value);
                    if (values.size() > limits.maxSharedStrings()) {
                        throw limit("Excel sharedStrings exceed limit: " + limits.maxSharedStrings());
                    }
                }
            }
            return values;
        } catch (VKOfficeException e) {
            throw e;
        } catch (Exception e) {
            throw parse("Read sharedStrings.xml failed", e);
        }
    }

    public static VKExcelSheet readSheet(Path sheetXml,
                                         String sheetName,
                                         List<String> sharedStrings,
                                         VKExcelLimits limits) {
        VKExcelSheet sheet = new VKExcelSheet(sheetName);
        readSheetRows(sheetXml, sharedStrings, limits, row -> {
            for (VKExcelCell cell : row.cells().values()) {
                sheet.addCell(cell);
            }
        });
        return sheet;
    }

    public static void readSheetRows(Path sheetXml,
                                     List<String> sharedStrings,
                                     VKExcelLimits limits,
                                     Consumer<VKExcelRowView> consumer) {
        XMLInputFactory factory = VKXmlSafeFactory.createInputFactory();
        List<String> sst = sharedStrings == null ? List.of() : sharedStrings;
        try (InputStream in = Files.newInputStream(sheetXml)) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            int rowCount = 0;
            int lastRowIndex = 0;
            while (r.hasNext()) {
                int e = r.next();
                if (e == XMLStreamConstants.START_ELEMENT && "row".equals(r.getLocalName())) {
                    int rowIndex = parseIntOrDefault(attr(r, "r"), lastRowIndex + 1);
                    if (rowIndex <= 0) {
                        rowIndex = lastRowIndex + 1;
                    }
                    lastRowIndex = rowIndex;
                    rowCount++;
                    if (rowCount > limits.maxRowsPerSheet()) {
                        throw limit("Excel rows exceed limit: " + limits.maxRowsPerSheet());
                    }
                    LinkedHashMap<Integer, VKExcelCell> rowCells = new LinkedHashMap<>();
                    int fallbackCol = 1;
                    while (r.hasNext()) {
                        int rowEvent = r.next();
                        if (rowEvent == XMLStreamConstants.START_ELEMENT && "c".equals(r.getLocalName())) {
                            ParsedCell parsed = parseCell(r, rowIndex, fallbackCol, sst, limits);
                            fallbackCol = parsed.col + 1;
                            if (parsed.col > limits.maxColsPerRow()) {
                                throw limit("Excel columns exceed limit: " + limits.maxColsPerRow());
                            }
                            rowCells.put(parsed.col, parsed.cell);
                        } else if (rowEvent == XMLStreamConstants.END_ELEMENT && "row".equals(r.getLocalName())) {
                            break;
                        }
                    }
                    consumer.accept(new VKExcelRowView(rowIndex, rowCells));
                }
            }
        } catch (VKOfficeException e) {
            throw e;
        } catch (Exception e) {
            throw parse("Read sheet xml failed: " + sheetXml.getFileName(), e);
        }
    }

    private static ParsedCell parseCell(XMLStreamReader r,
                                        int rowIndex,
                                        int fallbackCol,
                                        List<String> sharedStrings,
                                        VKExcelLimits limits) throws XMLStreamException {
        String cellRef = attr(r, "r");
        VKCellRef ref = VKCellRef.parse(cellRef, rowIndex, fallbackCol);
        String t = attr(r, "t");
        String formula = null;
        String rawValue = null;
        String inlineValue = null;

        while (r.hasNext()) {
            int e = r.next();
            if (e == XMLStreamConstants.START_ELEMENT) {
                String name = r.getLocalName();
                if ("f".equals(name)) {
                    formula = r.getElementText();
                    ensureCellChars(formula, limits);
                } else if ("v".equals(name)) {
                    rawValue = r.getElementText();
                    ensureCellChars(rawValue, limits);
                } else if ("is".equals(name)) {
                    inlineValue = readInlineString(r);
                    ensureCellChars(inlineValue, limits);
                }
            } else if (e == XMLStreamConstants.END_ELEMENT && "c".equals(r.getLocalName())) {
                break;
            }
        }

        VKExcelCell cell;
        if (formula != null) {
            cell = VKExcelCell.formulaCell(ref.row(), ref.col(), formula, rawValue);
        } else if ("s".equals(t)) {
            String value = resolveSharedString(rawValue, sharedStrings);
            ensureCellChars(value, limits);
            cell = VKExcelCell.stringCell(ref.row(), ref.col(), value);
        } else if ("inlineStr".equals(t) || "str".equals(t)) {
            String value = inlineValue != null ? inlineValue : rawValue;
            cell = VKExcelCell.stringCell(ref.row(), ref.col(), value);
        } else if ("b".equals(t)) {
            String boolValue = ("1".equals(rawValue) || "true".equalsIgnoreCase(rawValue)) ? "1" : "0";
            cell = new VKExcelCell(ref.row(), ref.col(), VKExcelCellType.BOOLEAN, boolValue, null);
        } else if (rawValue == null && inlineValue == null) {
            cell = VKExcelCell.blankCell(ref.row(), ref.col());
        } else {
            String value = rawValue != null ? rawValue : inlineValue;
            cell = new VKExcelCell(ref.row(), ref.col(), VKExcelCellType.NUMBER, value, null);
        }
        return new ParsedCell(ref.col(), cell);
    }

    private static String readInlineString(XMLStreamReader r) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        while (r.hasNext()) {
            int e = r.next();
            if (e == XMLStreamConstants.START_ELEMENT && "t".equals(r.getLocalName())) {
                sb.append(r.getElementText());
            } else if (e == XMLStreamConstants.END_ELEMENT && "is".equals(r.getLocalName())) {
                break;
            }
        }
        return sb.toString();
    }

    private static String readSharedStringItem(XMLStreamReader r) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        while (r.hasNext()) {
            int e = r.next();
            if (e == XMLStreamConstants.START_ELEMENT && "t".equals(r.getLocalName())) {
                sb.append(r.getElementText());
            } else if (e == XMLStreamConstants.END_ELEMENT && "si".equals(r.getLocalName())) {
                break;
            }
        }
        return sb.toString();
    }

    private static String resolveSharedString(String rawIndex, List<String> sharedStrings) {
        int idx = parseIntOrDefault(rawIndex, -1);
        if (idx < 0 || idx >= sharedStrings.size()) {
            throw parse("Invalid shared string index: " + rawIndex, null);
        }
        return sharedStrings.get(idx);
    }

    private static void ensureCellChars(String value, VKExcelLimits limits) {
        if (value != null && value.length() > limits.maxCellChars()) {
            throw limit("Excel cell chars exceed limit: " + limits.maxCellChars());
        }
    }

    private static String attr(XMLStreamReader r, String localName) {
        for (int i = 0; i < r.getAttributeCount(); i++) {
            if (localName.equals(r.getAttributeLocalName(i))) {
                return r.getAttributeValue(i);
            }
        }
        return null;
    }

    private static int parseIntOrDefault(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignore) {
            return fallback;
        }
    }

    private static VKOfficeException parse(String message, Exception cause) {
        return cause == null
                ? new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR, message)
                : new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR, message, cause);
    }

    private static VKOfficeException limit(String message) {
        return new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED, message);
    }

    private record ParsedCell(int col, VKExcelCell cell) {
    }
}
