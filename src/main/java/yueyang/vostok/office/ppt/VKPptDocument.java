package yueyang.vostok.office.ppt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** PPT 聚合读取结果。 */
public final class VKPptDocument {
    private final String text;
    private final int charCount;
    private final int slideCount;
    private final List<VKPptImage> images;

    public VKPptDocument(String text, int charCount, int slideCount, List<VKPptImage> images) {
        this.text = text == null ? "" : text;
        this.charCount = charCount;
        this.slideCount = slideCount;
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

    /** 幻灯片数。 */
    public int slideCount() {
        return slideCount;
    }

    /** 图片列表。 */
    public List<VKPptImage> images() {
        return images;
    }

    /** 图片数量（按出现次数）。 */
    public int imageCount() {
        return images.size();
    }
}
