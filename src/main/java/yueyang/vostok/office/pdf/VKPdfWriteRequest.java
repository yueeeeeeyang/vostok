package yueyang.vostok.office.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** PDF 写入请求，包含多页页面。 */
public final class VKPdfWriteRequest {
    private final List<VKPdfWritePage> pages = new ArrayList<>();

    /** 追加页面。 */
    public VKPdfWriteRequest addPage(VKPdfWritePage page) {
        if (page != null) {
            pages.add(page);
        }
        return this;
    }

    /** 新增一页并返回，便于链式追加元素。 */
    public VKPdfWritePage addPage() {
        VKPdfWritePage page = new VKPdfWritePage();
        pages.add(page);
        return page;
    }

    /** 返回只读页面列表。 */
    public List<VKPdfWritePage> pages() {
        return Collections.unmodifiableList(pages);
    }
}
