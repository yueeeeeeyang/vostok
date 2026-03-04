package yueyang.vostok.office.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** PDF 写入页面，元素按添加顺序输出。 */
public final class VKPdfWritePage {
    private final List<VKPdfWriteElement> elements = new ArrayList<>();

    /** 追加任意元素。 */
    public VKPdfWritePage addElement(VKPdfWriteElement element) {
        if (element != null) {
            elements.add(element);
        }
        return this;
    }

    /** 追加段落。 */
    public VKPdfWritePage addParagraph(String text) {
        elements.add(new VKPdfParagraphElement(text));
        return this;
    }

    /** 追加 bytes 图片。 */
    public VKPdfWritePage addImageBytes(String fileName, byte[] bytes) {
        elements.add(VKPdfImageElement.fromBytes(fileName, bytes));
        return this;
    }

    /** 追加文件路径图片。 */
    public VKPdfWritePage addImageFile(String filePath) {
        elements.add(VKPdfImageElement.fromFile(filePath));
        return this;
    }

    /** 返回只读元素列表。 */
    public List<VKPdfWriteElement> elements() {
        return Collections.unmodifiableList(elements);
    }
}
