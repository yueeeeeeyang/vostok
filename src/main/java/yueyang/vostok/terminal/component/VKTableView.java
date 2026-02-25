package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;
import yueyang.vostok.terminal.tool.VKTablePrinter;

import java.util.ArrayList;
import java.util.List;

public final class VKTableView extends VKView {
    private final List<String> columns = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();
    private VKTablePrinter.BorderStyle borderStyle = VKTablePrinter.BorderStyle.ASCII;

    public VKTableView columns(String... columns) {
        this.columns.clear();
        if (columns != null) {
            for (String column : columns) {
                this.columns.add(column == null ? "" : column);
            }
        }
        return this;
    }

    public VKTableView rows(List<List<String>> rows) {
        this.rows.clear();
        if (rows != null) {
            for (List<String> row : rows) {
                this.rows.add(row == null ? List.of() : new ArrayList<>(row));
            }
        }
        return this;
    }

    public VKTableView addRow(Object... values) {
        List<String> row = new ArrayList<>();
        if (values != null) {
            for (Object value : values) {
                row.add(value == null ? "" : String.valueOf(value));
            }
        }
        this.rows.add(row);
        return this;
    }

    public VKTableView borderStyle(VKTablePrinter.BorderStyle borderStyle) {
        if (borderStyle != null) {
            this.borderStyle = borderStyle;
        }
        return this;
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        VKTablePrinter table = new VKTablePrinter()
                .columns(columns.toArray(new String[0]))
                .rows(rows)
                .borderStyle(borderStyle);
        String rendered = table.render();
        if (rendered.isBlank()) {
            return List.of();
        }
        String[] lines = rendered.split("\\R");
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(ctx.fit(line));
        }
        return out;
    }
}
