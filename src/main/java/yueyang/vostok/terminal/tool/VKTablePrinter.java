package yueyang.vostok.terminal.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Text table renderer.
 */
public final class VKTablePrinter {
    public enum BorderStyle {
        ASCII,
        ROUNDED,
        NONE
    }

    private final List<String> columns = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();
    private int padding = 1;
    private BorderStyle borderStyle = BorderStyle.ASCII;

    public VKTablePrinter columns(String... values) {
        this.columns.clear();
        if (values != null) {
            this.columns.addAll(Arrays.asList(values));
        }
        return this;
    }

    public VKTablePrinter row(Object... values) {
        List<String> row = new ArrayList<>();
        if (values != null) {
            for (Object value : values) {
                row.add(value == null ? "" : String.valueOf(value));
            }
        }
        rows.add(row);
        return this;
    }

    public VKTablePrinter rows(List<List<String>> values) {
        this.rows.clear();
        if (values != null) {
            for (List<String> value : values) {
                this.rows.add(value == null ? List.of() : new ArrayList<>(value));
            }
        }
        return this;
    }

    public VKTablePrinter padding(int padding) {
        this.padding = Math.max(0, padding);
        return this;
    }

    public VKTablePrinter borderStyle(BorderStyle borderStyle) {
        if (borderStyle != null) {
            this.borderStyle = borderStyle;
        }
        return this;
    }

    public String render() {
        int colCount = resolveColCount();
        if (colCount == 0) {
            return "";
        }

        int[] widths = new int[colCount];
        for (int i = 0; i < colCount; i++) {
            widths[i] = VKTextWidth.width(cell(columns, i));
        }
        for (List<String> row : rows) {
            for (int i = 0; i < colCount; i++) {
                widths[i] = Math.max(widths[i], VKTextWidth.width(cell(row, i)));
            }
        }

        BorderChars border = BorderChars.of(borderStyle);
        StringBuilder out = new StringBuilder();
        if (borderStyle != BorderStyle.NONE) {
            out.append(line(border.topLeft, border.topMid, border.topRight, border.horizontal, widths)).append('\n');
        }
        out.append(renderRow(columns, widths, border.vertical)).append('\n');
        if (borderStyle != BorderStyle.NONE) {
            out.append(line(border.midLeft, border.center, border.midRight, border.horizontal, widths)).append('\n');
        } else {
            out.append("-".repeat(Math.max(1, VKTextWidth.width(renderRow(columns, widths, '|'))))).append('\n');
        }

        for (int i = 0; i < rows.size(); i++) {
            out.append(renderRow(rows.get(i), widths, border.vertical));
            if (i < rows.size() - 1) {
                out.append('\n');
            }
        }

        if (borderStyle != BorderStyle.NONE) {
            out.append('\n').append(line(border.bottomLeft, border.bottomMid, border.bottomRight, border.horizontal, widths));
        }
        return out.toString();
    }

    private String renderRow(List<String> row, int[] widths, char vertical) {
        StringBuilder sb = new StringBuilder();
        if (borderStyle != BorderStyle.NONE) {
            sb.append(vertical);
        }
        for (int i = 0; i < widths.length; i++) {
            String value = cell(row, i);
            sb.append(" ".repeat(padding));
            sb.append(VKTextWidth.padRight(value, widths[i]));
            sb.append(" ".repeat(padding));
            if (i < widths.length - 1) {
                sb.append(borderStyle == BorderStyle.NONE ? '|' : vertical);
            }
        }
        if (borderStyle != BorderStyle.NONE) {
            sb.append(vertical);
        }
        return sb.toString();
    }

    private String line(char left, char mid, char right, char horizontal, int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < widths.length; i++) {
            sb.append(String.valueOf(horizontal).repeat(widths[i] + padding * 2));
            if (i < widths.length - 1) {
                sb.append(mid);
            }
        }
        sb.append(right);
        return sb.toString();
    }

    private int resolveColCount() {
        int out = columns.size();
        for (List<String> row : rows) {
            out = Math.max(out, row == null ? 0 : row.size());
        }
        return out;
    }

    private String cell(List<String> row, int index) {
        if (row == null || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value;
    }

    private record BorderChars(
            char topLeft,
            char topMid,
            char topRight,
            char midLeft,
            char center,
            char midRight,
            char bottomLeft,
            char bottomMid,
            char bottomRight,
            char horizontal,
            char vertical
    ) {
        static BorderChars of(BorderStyle style) {
            if (style == BorderStyle.ROUNDED) {
                return new BorderChars('╭', '┬', '╮', '├', '┼', '┤', '╰', '┴', '╯', '─', '│');
            }
            if (style == BorderStyle.NONE) {
                return new BorderChars(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', '-', '|');
            }
            return new BorderChars('+', '+', '+', '+', '+', '+', '+', '+', '+', '-', '|');
        }
    }
}
