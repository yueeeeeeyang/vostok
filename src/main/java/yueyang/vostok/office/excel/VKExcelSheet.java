package yueyang.vostok.office.excel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Excel 工作表。 */
public final class VKExcelSheet {
    private final String name;
    private final LinkedHashMap<Integer, LinkedHashMap<Integer, VKExcelCell>> rows = new LinkedHashMap<>();

    public VKExcelSheet(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("sheet name is blank");
        }
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** 添加单元格；相同行列重复写入时以最后一次为准。 */
    public VKExcelSheet addCell(VKExcelCell cell) {
        if (cell == null) {
            return this;
        }
        rows.computeIfAbsent(cell.rowIndex(), k -> new LinkedHashMap<>())
                .put(cell.columnIndex(), cell);
        return this;
    }

    /** 按类型写入普通单元格。 */
    public VKExcelSheet setCell(int rowIndex, int columnIndex, VKExcelCellType type, String value) {
        return addCell(new VKExcelCell(rowIndex, columnIndex, type, value, null));
    }

    /** 写入公式单元格（公式文本 + 可选缓存值）。 */
    public VKExcelSheet setFormulaCell(int rowIndex, int columnIndex, String formula, String cachedValue) {
        return addCell(VKExcelCell.formulaCell(rowIndex, columnIndex, formula, cachedValue));
    }

    /** 读取指定行列的单元格，不存在返回 null。 */
    public VKExcelCell getCell(int rowIndex, int columnIndex) {
        Map<Integer, VKExcelCell> row = rows.get(rowIndex);
        return row == null ? null : row.get(columnIndex);
    }

    /** 返回按行组织的只读快照。 */
    public Map<Integer, Map<Integer, VKExcelCell>> rows() {
        LinkedHashMap<Integer, Map<Integer, VKExcelCell>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, LinkedHashMap<Integer, VKExcelCell>> entry : rows.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    /** 返回非空行数量。 */
    public int rowCount() {
        return rows.size();
    }

    /** 返回当前 sheet 中出现过的最大列号。 */
    public int maxColumnIndex() {
        int max = 0;
        for (Map<Integer, VKExcelCell> row : rows.values()) {
            for (Integer col : row.keySet()) {
                if (col != null && col > max) {
                    max = col;
                }
            }
        }
        return max;
    }

    /** 包内可见：写入器使用可变结构，避免二次拷贝。 */
    LinkedHashMap<Integer, LinkedHashMap<Integer, VKExcelCell>> mutableRows() {
        return rows;
    }
}
