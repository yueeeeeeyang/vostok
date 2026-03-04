package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.excel.VKExcelWorkbook;
import yueyang.vostok.office.excel.template.VKExcelTemplateOptions;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokOfficeExcelTemplateLargeFileTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        Vostok.Office.close();
        Vostok.File.close();
    }

    private void init() {
        Vostok.File.init(new VKFileConfig().baseDir(tempDir.toString()));
        Vostok.Office.init(new VKOfficeConfig());
    }

    @Test
    void testExpandedRowsLimitExceeded() {
        init();
        VKExcelSheet sheet = new VKExcelSheet("S1")
                .addCell(VKExcelCell.stringCell(1, 1, "{{#items as item keepPlaceholderRows=false}}"))
                .addCell(VKExcelCell.stringCell(2, 1, "{{item.name}}"))
                .addCell(VKExcelCell.stringCell(3, 1, "{{/items}}"));
        Vostok.Office.writeExcel("tpl/limit.xlsx", new VKExcelWorkbook().addSheet(sheet));

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            items.add(new LinkedHashMap<>(Map.of("name", "N-" + i)));
        }

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.renderExcelTemplate(
                        "tpl/limit.xlsx",
                        "out/limit.xlsx",
                        Map.of("items", items),
                        VKExcelTemplateOptions.defaults().maxExpandedRows(500)
                ));
        assertEquals(VKOfficeErrorCode.LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void testOutputBytesLimitExceeded() {
        init();
        VKExcelSheet sheet = new VKExcelSheet("S1")
                .addCell(VKExcelCell.stringCell(1, 1, "{{name}}"));
        Vostok.Office.writeExcel("tpl/size.xlsx", new VKExcelWorkbook().addSheet(sheet));

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.renderExcelTemplate(
                        "tpl/size.xlsx",
                        "out/size.xlsx",
                        Map.of("name", "Tom"),
                        VKExcelTemplateOptions.defaults().maxOutputBytes(64)
                ));
        assertEquals(VKOfficeErrorCode.LIMIT_EXCEEDED, ex.getErrorCode());
    }
}
