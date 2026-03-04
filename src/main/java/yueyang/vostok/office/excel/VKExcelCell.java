package yueyang.vostok.office.excel;

/**
 * Excel 单元格。
 * rowIndex/columnIndex 从 1 开始，保持与 Excel 行列编号一致。
 */
public final class VKExcelCell {
    private final int rowIndex;
    private final int columnIndex;
    private final VKExcelCellType type;
    private final String value;
    private final String formula;

    public VKExcelCell(int rowIndex, int columnIndex, VKExcelCellType type, String value, String formula) {
        if (rowIndex <= 0) {
            throw new IllegalArgumentException("rowIndex must be > 0");
        }
        if (columnIndex <= 0) {
            throw new IllegalArgumentException("columnIndex must be > 0");
        }
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
        this.type = type == null ? VKExcelCellType.BLANK : type;
        this.value = value;
        this.formula = formula;
    }

    public static VKExcelCell stringCell(int rowIndex, int columnIndex, String value) {
        return new VKExcelCell(rowIndex, columnIndex, VKExcelCellType.STRING, value, null);
    }

    public static VKExcelCell numberCell(int rowIndex, int columnIndex, String value) {
        return new VKExcelCell(rowIndex, columnIndex, VKExcelCellType.NUMBER, value, null);
    }

    public static VKExcelCell booleanCell(int rowIndex, int columnIndex, boolean value) {
        return new VKExcelCell(rowIndex, columnIndex, VKExcelCellType.BOOLEAN, value ? "1" : "0", null);
    }

    public static VKExcelCell blankCell(int rowIndex, int columnIndex) {
        return new VKExcelCell(rowIndex, columnIndex, VKExcelCellType.BLANK, null, null);
    }

    public static VKExcelCell formulaCell(int rowIndex, int columnIndex, String formula, String cachedValue) {
        return new VKExcelCell(rowIndex, columnIndex, VKExcelCellType.FORMULA, cachedValue, formula);
    }

    public int rowIndex() {
        return rowIndex;
    }

    public int columnIndex() {
        return columnIndex;
    }

    public VKExcelCellType type() {
        return type;
    }

    public String value() {
        return value;
    }

    public String formula() {
        return formula;
    }
}
