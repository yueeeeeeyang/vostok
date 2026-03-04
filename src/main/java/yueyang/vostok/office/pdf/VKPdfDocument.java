package yueyang.vostok.office.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** PDF 聚合读取结果。 */
public final class VKPdfDocument {
    private final String text;
    private final int charCount;
    private final int pageCount;
    private final List<VKPdfImage> images;

    public VKPdfDocument(String text, int charCount, int pageCount, List<VKPdfImage> images) {
        this.text = text == null ? "" : text;
        this.charCount = charCount;
        this.pageCount = pageCount;
        this.images = images == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(images));
    }

    /** 文档文本。 */
    public String text() {
        return text;
    }

    /** 文档字数（按非空白 Unicode code point 统计）。 */
    public int charCount() {
        return charCount;
    }

    /** 页数。 */
    public int pageCount() {
        return pageCount;
    }

    /** 图片列表。 */
    public List<VKPdfImage> images() {
        return images;
    }

    /** 图片数量（按出现次数）。 */
    public int imageCount() {
        return images.size();
    }
}
