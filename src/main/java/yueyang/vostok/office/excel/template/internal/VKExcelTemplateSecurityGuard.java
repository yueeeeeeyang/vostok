package yueyang.vostok.office.excel.template.internal;

import yueyang.vostok.office.excel.internal.VKExcelSecurityGuard;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

/** Excel 模板渲染安全守卫。 */
public final class VKExcelTemplateSecurityGuard {

    private VKExcelTemplateSecurityGuard() {
    }

    public static void assertTemplatePath(String path) {
        VKExcelSecurityGuard.assertSafePath(path);
        requireXlsx(path, "Excel template path must be .xlsx: ");
    }

    public static void assertOutputPath(String path) {
        VKExcelSecurityGuard.assertSafePath(path);
        requireXlsx(path, "Excel output path must be .xlsx: ");
    }

    private static void requireXlsx(String path, String prefix) {
        if (path == null || !path.toLowerCase().endsWith(".xlsx")) {
            throw new VKOfficeException(VKOfficeErrorCode.UNSUPPORTED_FORMAT, prefix + path);
        }
    }
}
