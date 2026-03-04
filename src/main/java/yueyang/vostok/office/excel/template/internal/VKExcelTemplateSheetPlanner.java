package yueyang.vostok.office.excel.template.internal;

import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.excel.template.internal.VKExcelTemplateMarkerParser.Marker;
import yueyang.vostok.office.excel.template.internal.VKExcelTemplateMarkerParser.MarkerType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sheet 行块规划器（普通行 / 循环块 / 条件块）。 */
public final class VKExcelTemplateSheetPlanner {

    public List<Node> plan(VKExcelSheet sheet) {
        if (sheet == null) {
            return List.of();
        }
        List<RowSnapshot> rows = toRows(sheet);
        List<Node> out = new ArrayList<>();
        int i = 0;
        while (i < rows.size()) {
            RowSnapshot row = rows.get(i);
            Marker marker = row.marker();
            if (marker == null) {
                out.add(new NormalNode(row));
                i++;
                continue;
            }
            if (marker.type() == MarkerType.END) {
                throw parse("Orphan end marker at row " + row.rowIndex() + ": " + marker.key());
            }
            if (marker.type() == MarkerType.LOOP_START) {
                VKExcelTemplateMarkerParser.assertAlias(marker.alias());
                int endIdx = findEnd(rows, i + 1, marker.key());
                List<RowSnapshot> templateRows = copyRange(rows, i + 1, endIdx);
                out.add(new LoopNode(row, rows.get(endIdx), templateRows, marker.key(), marker.alias(), marker.keepPlaceholderRows()));
                i = endIdx + 1;
                continue;
            }
            if (marker.type() == MarkerType.COND_START) {
                int endIdx = findEnd(rows, i + 1, marker.key());
                List<RowSnapshot> templateRows = copyRange(rows, i + 1, endIdx);
                out.add(new CondNode(row, rows.get(endIdx), templateRows, marker.key()));
                i = endIdx + 1;
                continue;
            }
            throw parse("Unsupported marker at row " + row.rowIndex() + ": " + marker);
        }
        return out;
    }

    private List<RowSnapshot> toRows(VKExcelSheet sheet) {
        Map<Integer, Map<Integer, VKExcelCell>> rowsMap = sheet.rows();
        List<Integer> rowIndexes = new ArrayList<>(rowsMap.keySet());
        rowIndexes.sort(Comparator.naturalOrder());

        List<RowSnapshot> out = new ArrayList<>(rowIndexes.size());
        for (Integer rowIndex : rowIndexes) {
            if (rowIndex == null || rowIndex <= 0) {
                continue;
            }
            Map<Integer, VKExcelCell> rowCells = rowsMap.get(rowIndex);
            LinkedHashMap<Integer, VKExcelCell> sorted = new LinkedHashMap<>();
            List<Integer> cols = new ArrayList<>(rowCells.keySet());
            cols.sort(Comparator.naturalOrder());
            Marker marker = null;
            int markerCol = -1;
            for (Integer col : cols) {
                VKExcelCell cell = rowCells.get(col);
                sorted.put(col, cell);
                if (cell == null || cell.value() == null) {
                    continue;
                }
                Marker parsed = VKExcelTemplateMarkerParser.parse(cell.value());
                if (parsed != null) {
                    if (marker != null) {
                        throw parse("Multiple markers in one row is not allowed: row=" + rowIndex);
                    }
                    marker = parsed;
                    markerCol = col == null ? -1 : col;
                }
            }
            out.add(new RowSnapshot(rowIndex, sorted, marker, markerCol));
        }
        return out;
    }

    private int findEnd(List<RowSnapshot> rows, int start, String key) {
        for (int i = start; i < rows.size(); i++) {
            Marker marker = rows.get(i).marker();
            if (marker == null) {
                continue;
            }
            if (marker.type() == MarkerType.END && key.equals(marker.key())) {
                return i;
            }
            throw parse("Nested/mismatched marker is not supported. row=" + rows.get(i).rowIndex());
        }
        throw parse("Missing end marker for: " + key);
    }

    private List<RowSnapshot> copyRange(List<RowSnapshot> rows, int from, int toExclusive) {
        if (from >= toExclusive) {
            return List.of();
        }
        List<RowSnapshot> out = new ArrayList<>(toExclusive - from);
        for (int i = from; i < toExclusive; i++) {
            RowSnapshot src = rows.get(i);
            out.add(new RowSnapshot(src.rowIndex(), new LinkedHashMap<>(src.cells()), src.marker(), src.markerCol()));
        }
        return out;
    }

    private VKOfficeException parse(String message) {
        return new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR, message);
    }

    public sealed interface Node permits NormalNode, LoopNode, CondNode {
    }

    /** 普通行节点。 */
    public record NormalNode(RowSnapshot row) implements Node {
    }

    /** 循环块节点。 */
    public record LoopNode(RowSnapshot startRow,
                           RowSnapshot endRow,
                           List<RowSnapshot> templateRows,
                           String listKey,
                           String alias,
                           Boolean keepPlaceholderRows) implements Node {
    }

    /** 条件块节点。 */
    public record CondNode(RowSnapshot startRow,
                           RowSnapshot endRow,
                           List<RowSnapshot> templateRows,
                           String condKey) implements Node {
    }

    /** 行快照。 */
    public record RowSnapshot(int rowIndex,
                              LinkedHashMap<Integer, VKExcelCell> cells,
                              Marker marker,
                              int markerCol) {
    }
}
