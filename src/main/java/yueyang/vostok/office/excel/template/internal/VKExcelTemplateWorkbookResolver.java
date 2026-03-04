package yueyang.vostok.office.excel.template.internal;

import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.excel.VKExcelWorkbook;
import yueyang.vostok.office.excel.template.VKExcelTemplateOptions;
import yueyang.vostok.office.template.VKOfficeTemplateOptions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 工作簿模板渲染分发器。 */
public final class VKExcelTemplateWorkbookResolver {
    private final VKExcelTemplateSheetPlanner planner = new VKExcelTemplateSheetPlanner();
    private final VKExcelTemplateSheetExpander expander = new VKExcelTemplateSheetExpander();

    public VKExcelWorkbook resolve(VKExcelWorkbook source,
                                   Map<String, Object> data,
                                   VKExcelTemplateOptions options,
                                   VKOfficeTemplateOptions templateOptions) {
        VKExcelTemplateOptions opt = options == null ? VKExcelTemplateOptions.defaults() : options;
        VKOfficeTemplateOptions tpl = templateOptions == null ? VKOfficeTemplateOptions.defaults() : templateOptions;
        Set<String> selected = opt.targetSheets().stream().collect(Collectors.toSet());

        VKExcelWorkbook out = new VKExcelWorkbook();
        for (VKExcelSheet sheet : source.sheets()) {
            if (sheet == null) {
                continue;
            }
            if (!selected.isEmpty() && !selected.contains(sheet.name())) {
                out.addSheet(copySheet(sheet));
                continue;
            }
            List<VKExcelTemplateSheetPlanner.Node> nodes = planner.plan(sheet);
            VKExcelSheet rendered = expander.expand(sheet.name(), nodes, data, opt, tpl);
            out.addSheet(rendered);
        }
        return out;
    }

    private VKExcelSheet copySheet(VKExcelSheet source) {
        VKExcelSheet out = new VKExcelSheet(source.name());
        Map<Integer, Map<Integer, VKExcelCell>> rows = source.rows();
        for (Map.Entry<Integer, Map<Integer, VKExcelCell>> rowEntry : rows.entrySet()) {
            Integer rowIndex = rowEntry.getKey();
            if (rowIndex == null || rowIndex <= 0) {
                continue;
            }
            for (Map.Entry<Integer, VKExcelCell> cellEntry : rowEntry.getValue().entrySet()) {
                Integer col = cellEntry.getKey();
                VKExcelCell cell = cellEntry.getValue();
                if (cell == null || col == null || col <= 0) {
                    continue;
                }
                out.addCell(new VKExcelCell(rowIndex, col, cell.type(), cell.value(), cell.formula()));
            }
        }
        return out;
    }
}
