package yueyang.vostok.office.excel.template.internal;

import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.template.VKOfficeTemplateOptions;
import yueyang.vostok.office.excel.template.VKExcelTemplateOptions;
import yueyang.vostok.office.excel.template.internal.VKExcelTemplateSheetPlanner.CondNode;
import yueyang.vostok.office.excel.template.internal.VKExcelTemplateSheetPlanner.LoopNode;
import yueyang.vostok.office.excel.template.internal.VKExcelTemplateSheetPlanner.Node;
import yueyang.vostok.office.excel.template.internal.VKExcelTemplateSheetPlanner.NormalNode;
import yueyang.vostok.office.excel.template.internal.VKExcelTemplateSheetPlanner.RowSnapshot;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sheet 行块展开器。 */
public final class VKExcelTemplateSheetExpander {

    public VKExcelSheet expand(String sheetName,
                               List<Node> nodes,
                               Map<String, Object> rootContext,
                               VKExcelTemplateOptions options,
                               VKOfficeTemplateOptions templateOptions) {
        List<Node> plan = nodes == null ? List.of() : nodes;
        Map<String, Object> root = rootContext == null ? Map.of() : rootContext;
        VKExcelTemplateOptions opt = options == null ? VKExcelTemplateOptions.defaults() : options;
        VKOfficeTemplateOptions tplOpt = templateOptions == null ? VKOfficeTemplateOptions.defaults() : templateOptions;

        VKExcelSheet out = new VKExcelSheet(sheetName);
        int nextRow = 1;
        boolean initialized = false;
        long expanded = 0;

        for (Node node : plan) {
            if (node instanceof NormalNode normal) {
                RowSnapshot row = normal.row();
                int preferred = row.rowIndex();
                if (!initialized) {
                    nextRow = Math.max(1, preferred);
                    initialized = true;
                }
                // 只在首个节点对齐原始行号，后续连续写出；
                // 这样当占位行被删除时，后续数据会自然前移，不会留下空洞行号。
                renderRow(out, row, nextRow, root, tplOpt, -1, false);
                nextRow++;
                expanded++;
                assertExpanded(expanded, opt.maxExpandedRows());
                continue;
            }

            if (node instanceof LoopNode loop) {
                int preferred = loop.startRow().rowIndex();
                if (!initialized) {
                    nextRow = Math.max(1, preferred);
                    initialized = true;
                }
                boolean keepRows = loop.keepPlaceholderRows() != null
                        ? loop.keepPlaceholderRows()
                        : opt.defaultKeepPlaceholderRows();
                if (keepRows) {
                    renderRow(out, loop.startRow(), nextRow, root, tplOpt, loop.startRow().markerCol(), true);
                    nextRow++;
                    expanded++;
                    assertExpanded(expanded, opt.maxExpandedRows());
                }

                List<Object> items = toList(resolvePath(root, loop.listKey()));
                for (Object item : items) {
                    Map<String, Object> ctx = new LinkedHashMap<>(root);
                    ctx.put(loop.alias(), item);
                    for (RowSnapshot templateRow : loop.templateRows()) {
                        renderRow(out, templateRow, nextRow, ctx, tplOpt, -1, false);
                        nextRow++;
                        expanded++;
                        assertExpanded(expanded, opt.maxExpandedRows());
                    }
                }

                if (keepRows) {
                    renderRow(out, loop.endRow(), nextRow, root, tplOpt, loop.endRow().markerCol(), true);
                    nextRow++;
                    expanded++;
                    assertExpanded(expanded, opt.maxExpandedRows());
                }
                continue;
            }

            if (node instanceof CondNode cond) {
                int preferred = cond.startRow().rowIndex();
                if (!initialized) {
                    nextRow = Math.max(1, preferred);
                    initialized = true;
                }
                if (isTruthy(resolvePath(root, cond.condKey()))) {
                    for (RowSnapshot templateRow : cond.templateRows()) {
                        renderRow(out, templateRow, nextRow, root, tplOpt, -1, false);
                        nextRow++;
                        expanded++;
                        assertExpanded(expanded, opt.maxExpandedRows());
                    }
                }
                continue;
            }
        }
        return out;
    }

    private void renderRow(VKExcelSheet out,
                           RowSnapshot sourceRow,
                           int targetRow,
                           Map<String, Object> context,
                           VKOfficeTemplateOptions templateOptions,
                           int markerCol,
                           boolean clearMarkerCell) {
        for (Map.Entry<Integer, VKExcelCell> e : sourceRow.cells().entrySet()) {
            Integer col = e.getKey();
            VKExcelCell sourceCell = e.getValue();
            if (sourceCell == null || col == null || col <= 0) {
                continue;
            }
            VKExcelCell outCell;
            if (clearMarkerCell && col == markerCol) {
                outCell = VKExcelTemplateCellResolver.clearCell(sourceCell, targetRow);
            } else {
                outCell = VKExcelTemplateCellResolver.renderCell(sourceCell, targetRow, context, templateOptions);
            }
            out.addCell(outCell);
        }
    }

    private void assertExpanded(long actual, long limit) {
        if (limit > 0 && actual > limit) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Excel template expanded rows exceed limit: " + actual + " > " + limit);
        }
    }

    private static Object resolvePath(Map<String, Object> data, String key) {
        if (data == null || data.isEmpty() || key == null || key.isBlank()) {
            return null;
        }
        if (!key.contains(".")) {
            return data.get(key);
        }
        String[] parts = key.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private static List<Object> toList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> out = new ArrayList<>();
            for (Object o : iterable) {
                out.add(o);
            }
            return out;
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<Object> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                out.add(Array.get(value, i));
            }
            return out;
        }
        return List.of(value);
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence cs) {
            return cs.length() > 0;
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }
}
