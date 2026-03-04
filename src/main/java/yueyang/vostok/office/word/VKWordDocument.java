package yueyang.vostok.office.word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Word 聚合读取结果。 */
public final class VKWordDocument {
    private final String text;
    private final int charCount;
    private final List<VKWordImage> images;
    private final int imageCount;

    public VKWordDocument(String text, int charCount, List<VKWordImage> images) {
        this.text = text == null ? "" : text;
        this.charCount = Math.max(0, charCount);
        List<VKWordImage> img = images == null ? List.of() : images;
        this.images = Collections.unmodifiableList(new ArrayList<>(img));
        this.imageCount = this.images.size();
    }

    /** 文档文本。 */
    public String text() {
        return text;
    }

    /** 文档字数（按非空白 Unicode code point 统计）。 */
    public int charCount() {
        return charCount;
    }

    /** 图片列表。 */
    public List<VKWordImage> images() {
        return images;
    }

    /** 图片数量（按出现次数）。 */
    public int imageCount() {
        return imageCount;
    }
}
