package yueyang.vostok.office.excel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 流式读取时的行视图。 */
public final class VKExcelRowView {
    private final int rowIndex;
    private final Map<Integer, VKExcelCell> cells;

    public VKExcelRowView(int rowIndex, Map<Integer, VKExcelCell> cells) {
        if (rowIndex <= 0) {
            throw new IllegalArgumentException("rowIndex must be > 0");
        }
        this.rowIndex = rowIndex;
        this.cells = Collections.unmodifiableMap(new LinkedHashMap<>(cells == null ? Map.of() : cells));
    }

    /** 当前行号（从 1 开始）。 */
    public int rowIndex() {
        return rowIndex;
    }

    /** 当前行单元格快照，key=列号（从 1 开始）。 */
    public Map<Integer, VKExcelCell> cells() {
        return cells;
    }
}
