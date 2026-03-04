package yueyang.vostok.office.excel.template.internal;

import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.template.VKOfficeTemplateOptions;
import yueyang.vostok.office.template.internal.VKOfficeTemplateEngine;

import java.util.Map;

/** Excel 单元格模板渲染器。 */
public final class VKExcelTemplateCellResolver {

    private VKExcelTemplateCellResolver() {
    }

    public static VKExcelCell renderCell(VKExcelCell source,
                                         int targetRowIndex,
                                         Map<String, Object> context,
                                         VKOfficeTemplateOptions templateOptions) {
        if (source == null) {
            return null;
        }
        String value = source.value();
        String formula = source.formula();
        if (value != null) {
            value = VKOfficeTemplateEngine.render(value, context, templateOptions);
        }
        if (formula != null) {
            formula = VKOfficeTemplateEngine.render(formula, context, templateOptions);
        }
        return new VKExcelCell(targetRowIndex, source.columnIndex(), source.type(), value, formula);
    }

    public static VKExcelCell clearCell(VKExcelCell source, int targetRowIndex) {
        if (source == null) {
            return null;
        }
        return new VKExcelCell(targetRowIndex, source.columnIndex(), source.type(), "", null);
    }
}
