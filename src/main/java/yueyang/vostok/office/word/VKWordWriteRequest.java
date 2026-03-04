package yueyang.vostok.office.word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Word 写入请求，元素按添加顺序输出到正文。 */
public final class VKWordWriteRequest {
    private final List<VKWordWriteElement> elements = new ArrayList<>();

    /** 追加任意写入元素。 */
    public VKWordWriteRequest addElement(VKWordWriteElement element) {
        if (element != null) {
            elements.add(element);
        }
        return this;
    }

    /** 追加段落。 */
    public VKWordWriteRequest addParagraph(String text) {
        elements.add(new VKWordParagraphElement(text));
        return this;
    }

    /** 追加 bytes 图片。 */
    public VKWordWriteRequest addImageBytes(String fileName, byte[] bytes) {
        elements.add(VKWordImageElement.fromBytes(fileName, bytes));
        return this;
    }

    /** 追加文件路径图片。 */
    public VKWordWriteRequest addImageFile(String filePath) {
        elements.add(VKWordImageElement.fromFile(filePath));
        return this;
    }

    /** 返回只读元素列表。 */
    public List<VKWordWriteElement> elements() {
        return Collections.unmodifiableList(elements);
    }
}
