package yueyang.vostok.office.excel.template.internal;

import yueyang.vostok.office.excel.VKExcelWorkbook;
import yueyang.vostok.office.excel.template.VKExcelTemplateOptions;
import yueyang.vostok.office.template.VKOfficeTemplateOptions;

import java.util.Map;

/** Excel 模板渲染总入口。 */
public final class VKExcelTemplateRenderer {
    private final VKExcelTemplateWorkbookResolver workbookResolver = new VKExcelTemplateWorkbookResolver();

    public VKExcelWorkbook renderWorkbook(VKExcelWorkbook templateWorkbook,
                                          Map<String, Object> data,
                                          VKExcelTemplateOptions options) {
        VKExcelTemplateOptions opt = options == null ? VKExcelTemplateOptions.defaults() : options;
        VKOfficeTemplateOptions templateOptions = VKOfficeTemplateOptions.defaults()
                .strictVariable(opt.strictVariable());
        return workbookResolver.resolve(templateWorkbook, data, opt, templateOptions);
    }
}
