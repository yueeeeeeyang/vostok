package yueyang.vostok.office.excel.internal;

/** Excel 单元格引用工具，例如 A1 -> (1,1)。 */
public final class VKCellRef {
    private final int row;
    private final int col;

    private VKCellRef(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public static VKCellRef of(int row, int col) {
        return new VKCellRef(row, col);
    }

    public static VKCellRef parse(String ref, int fallbackRow, int fallbackCol) {
        if (ref == null || ref.isBlank()) {
            return of(fallbackRow, fallbackCol);
        }
        String v = ref.trim();
        int i = 0;
        int col = 0;
        while (i < v.length()) {
            char c = v.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                col = col * 26 + (c - 'A' + 1);
                i++;
                continue;
            }
            if (c >= 'a' && c <= 'z') {
                col = col * 26 + (c - 'a' + 1);
                i++;
                continue;
            }
            break;
        }
        int row = fallbackRow;
        if (i < v.length()) {
            try {
                row = Integer.parseInt(v.substring(i));
            } catch (NumberFormatException ignore) {
                row = fallbackRow;
            }
        }
        if (col <= 0) {
            col = fallbackCol;
        }
        if (row <= 0) {
            row = fallbackRow;
        }
        return of(row, col);
    }

    public static String toRef(int row, int col) {
        return toColLetters(col) + row;
    }

    public static String toColLetters(int col) {
        if (col <= 0) {
            throw new IllegalArgumentException("col must be > 0");
        }
        StringBuilder sb = new StringBuilder();
        int v = col;
        while (v > 0) {
            int rem = (v - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            v = (v - 1) / 26;
        }
        return sb.toString();
    }

    public int row() {
        return row;
    }

    public int col() {
        return col;
    }
}
