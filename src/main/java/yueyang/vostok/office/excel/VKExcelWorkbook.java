package yueyang.vostok.office.excel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Excel 工作簿。 */
public final class VKExcelWorkbook {
    private final List<VKExcelSheet> sheets = new ArrayList<>();

    /** 追加一个工作表，按添加顺序保存。 */
    public VKExcelWorkbook addSheet(VKExcelSheet sheet) {
        if (sheet != null) {
            sheets.add(sheet);
        }
        return this;
    }

    /** 返回只读工作表列表。 */
    public List<VKExcelSheet> sheets() {
        return Collections.unmodifiableList(sheets);
    }

    /** 按名称查找工作表，不存在返回 null。 */
    public VKExcelSheet getSheet(String name) {
        if (name == null) {
            return null;
        }
        for (VKExcelSheet sheet : sheets) {
            if (name.equals(sheet.name())) {
                return sheet;
            }
        }
        return null;
    }
}
